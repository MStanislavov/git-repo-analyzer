package com.codemaster.git_repo_analyzer.sonar_data_collector;

import static com.codemaster.git_repo_analyzer.util.Constants.*;

import com.codemaster.git_repo_analyzer.event.*;
import com.codemaster.git_repo_analyzer.persistence.RepositoryAnalysisEntity;
import com.codemaster.git_repo_analyzer.persistence.RepositoryAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Component
class SonarDataAnalyzedListener {

  private static final Logger logger = LoggerFactory.getLogger(SonarDataAnalyzedListener.class);

  private static final String METRIC_KEYS = "ncloc,bugs,vulnerabilities,code_smells,sqale_index,"
      + "coverage,duplicated_lines_density,reliability_rating,security_rating,sqale_rating,alert_status";

  private final ApplicationEventPublisher eventPublisher;
  private final String sonarqubeUrl;
  private final String sonarqubeAuth;
  private final RestTemplate restTemplate;
  private final RepositoryAnalysisRepository analysisRepository;

  public SonarDataAnalyzedListener(ApplicationEventPublisher eventPublisher,
      @Value("${sonarqube-url}") String sonarqubeUrl,
      @Value("${sonarqube-auth}") String sonarqubeAuth,
      RestTemplate restTemplate,
      RepositoryAnalysisRepository analysisRepository) {
    this.eventPublisher = eventPublisher;
    this.sonarqubeUrl = sonarqubeUrl;
    this.sonarqubeAuth = sonarqubeAuth;
    this.restTemplate = restTemplate;
    this.analysisRepository = analysisRepository;
    setupRestTemplate();
  }

  @Async
  @EventListener
  public void onRepositoryAnalyzed(RepositoryAnalyzedEvent event) {
    if (event.getEventStatus().equals(EventStatus.FAILED)) {
      publishDataAnalyzedEvent(event, EventStatus.FAILED);
    } else if (event.getEventStatus().equals(EventStatus.SUCCEEDED)) {
      processSonarProjectData(event);
    }
  }

  private void setupRestTemplate() {
    restTemplate.getInterceptors().add((request, body, execution) -> {
      String auth = sonarqubeAuth + ":";
      byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes());
      String authHeader = "Basic " + new String(encodedAuth);
      request.getHeaders().set("Authorization", authHeader);
      return execution.execute(request, body);
    });
  }

  private void processSonarProjectData(RepositoryAnalyzedEvent analyzedEvent) {
    publishDataAnalyzedEvent(analyzedEvent, EventStatus.IN_PROGRESS);
    String projectKey = analyzedEvent.getProjectKey();
    try {
      waitForComputeEngine(projectKey);
      String apiUrl = String.format("%s/api/measures/component?component=%s&metricKeys=%s",
          sonarqubeUrl, projectKey, METRIC_KEYS);
      HttpHeaders headers = new HttpHeaders();
      HttpEntity<String> entity = new HttpEntity<>(headers);
      ResponseEntity<Map<String, Map<String, Object>>> response = restTemplate.exchange(
          apiUrl,
          HttpMethod.GET,
          entity, new ParameterizedTypeReference<>() {
          }
      );
      Map<String, Map<String, Object>> responseBody = response.getBody();
      if (responseBody != null) {
        extractAndPersistMetrics(responseBody, projectKey, analyzedEvent);
      } else {
        logger.warn("No data received from SonarQube for project: {}", projectKey);
        updateAnalysisFailure(projectKey, "No data received from SonarQube");
        publishDataAnalyzedEvent(analyzedEvent, EventStatus.FAILED);
      }
    } catch (RestClientException e) {
      logException(projectKey, e);
      updateAnalysisFailure(projectKey, "SonarQube API error: " + e.getMessage());
      publishDataAnalyzedEvent(analyzedEvent, EventStatus.FAILED);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      updateAnalysisFailure(projectKey, "Interrupted while waiting for CE task");
      publishDataAnalyzedEvent(analyzedEvent, EventStatus.FAILED);
    }
  }

  @SuppressWarnings("unchecked")
  private void waitForComputeEngine(String projectKey) throws InterruptedException {
    String ceUrl = String.format("%s/api/ce/component?component=%s", sonarqubeUrl, projectKey);
    int maxAttempts = 30;
    for (int i = 0; i < maxAttempts; i++) {
      try {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            ceUrl, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()),
            new ParameterizedTypeReference<>() {});
        Map<String, Object> body = response.getBody();
        if (body != null) {
          List<Object> queue = (List<Object>) body.get("queue");
          Map<String, Object> current = (Map<String, Object>) body.get("current");
          boolean queueEmpty = queue == null || queue.isEmpty();
          boolean taskDone = current != null && "SUCCESS".equals(current.get("status"));
          if (queueEmpty && taskDone) {
            logger.info("CE task completed for project: {}", projectKey);
            return;
          }
        }
      } catch (HttpClientErrorException e) {
        if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
          logger.error("SonarQube authentication failed ({}). Check sonarqube-auth token.", e.getStatusCode());
          throw e;
        }
        logger.debug("CE status check failed for {}: {}", projectKey, e.getMessage());
      } catch (RestClientException e) {
        logger.debug("CE status check failed for {}: {}", projectKey, e.getMessage());
      }
      logger.info("Waiting for CE task to complete for project: {} (attempt {}/{})", projectKey, i + 1, maxAttempts);
      Thread.sleep(2000);
    }
    logger.warn("CE task did not complete within timeout for project: {}", projectKey);
  }

  private void extractAndPersistMetrics(Map<String, Map<String, Object>> response,
      String projectKey, RepositoryAnalyzedEvent event) {
    Map<String, Object> component = response.get("component");
    if (component == null) {
      updateAnalysisFailure(projectKey, "No component data in SonarQube response");
      publishDataAnalyzedEvent(event, EventStatus.FAILED);
      return;
    }

    List<Map<String, Object>> measures = getMeasures(component);
    Map<String, String> metricMap = new HashMap<>();
    for (Map<String, Object> measure : measures) {
      String metric = (String) measure.get("metric");
      String value = (String) measure.get("value");
      metricMap.put(metric, value);
    }

    Optional<RepositoryAnalysisEntity> entityOpt = analysisRepository.findAll().stream()
        .filter(e -> projectKey.equals(e.getProjectKey()))
        .findFirst();

    if (entityOpt.isEmpty()) {
      logger.warn("No analysis entity found for project key: {}", projectKey);
      publishDataAnalyzedEvent(event, EventStatus.FAILED);
      return;
    }

    RepositoryAnalysisEntity entity = entityOpt.get();
    entity.setNcloc(parseIntOrNull(metricMap.get("ncloc")));
    entity.setBugs(parseIntOrNull(metricMap.get("bugs")));
    entity.setVulnerabilities(parseIntOrNull(metricMap.get("vulnerabilities")));
    entity.setCodeSmells(parseIntOrNull(metricMap.get("code_smells")));
    entity.setSqaleIndex(parseIntOrNull(metricMap.get("sqale_index")));
    entity.setCoverage(parseDoubleOrNull(metricMap.get("coverage")));
    entity.setDuplicatedLinesDensity(parseDoubleOrNull(metricMap.get("duplicated_lines_density")));
    entity.setReliabilityRating(parseDoubleOrNull(metricMap.get("reliability_rating")));
    entity.setSecurityRating(parseDoubleOrNull(metricMap.get("security_rating")));
    entity.setSqaleRating(parseDoubleOrNull(metricMap.get("sqale_rating")));
    entity.setAlertStatus(metricMap.get("alert_status"));
    entity.setAnalysisStatus(STATUS_SUCCEEDED);
    entity.setCurrentStep(STEP_COMPLETED);
    entity.setErrorMessage(null);
    entity.setAnalyzedAt(Timestamp.from(Instant.now()));
    analysisRepository.save(entity);

    int debtMinutes = entity.getSqaleIndex() != null ? entity.getSqaleIndex() : 0;
    int debtHours = convertMinutesToHours(debtMinutes);
    logger.info("Metrics collected for project {}: debt={}h, bugs={}, vulns={}, smells={}",
        projectKey, debtHours, entity.getBugs(), entity.getVulnerabilities(), entity.getCodeSmells());

    event.setMessage(String.valueOf(debtHours));
    publishDataAnalyzedEvent(event, EventStatus.SUCCEEDED);
  }

  private void updateAnalysisFailure(String projectKey, String errorMessage) {
    analysisRepository.findAll().stream()
        .filter(e -> projectKey.equals(e.getProjectKey()))
        .findFirst()
        .ifPresent(entity -> {
          entity.setAnalysisStatus(STATUS_FAILED);
          entity.setErrorMessage(errorMessage);
          analysisRepository.save(entity);
        });
  }

  private static Integer parseIntOrNull(String value) {
    if (value == null) return null;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Double parseDoubleOrNull(String value) {
    if (value == null) return null;
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static void logException(String projectKey, RestClientException e) {
    switch (e) {
    case HttpClientErrorException clientError ->
        logger.error("Client error while fetching SonarQube data for project {}: {}", projectKey, clientError.getStatusCode());
    case HttpServerErrorException serverError ->
        logger.error("Server error while fetching SonarQube data for project {}: {}", projectKey, serverError.getStatusCode());
    default -> logger.error("Error fetching SonarQube data for project {}: {}", projectKey, e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> getMeasures(Map<String, Object> component) {
    return (List<Map<String, Object>>) component.get("measures");
  }

  private int convertMinutesToHours(int debtMinutes) {
    if (debtMinutes < 1) return 0;
    return debtMinutes < 60 ? 1 : debtMinutes / 60;
  }

  private void publishDataAnalyzedEvent(RepositoryAnalyzedEvent event, EventStatus status) {
    DataAnalyzedEvent dataAnalyzedEvent = new DataAnalyzedEvent(
        this,
        EventType.DATA_COLLECTED,
        status.equals(EventStatus.FAILED) ? "could not gather data" : event.getMessage(),
        status,
        Timestamp.from(Instant.now()),
        UUID.randomUUID(),
        event.getJobId(),
        event.getProjectKey()
    );
    eventPublisher.publishEvent(dataAnalyzedEvent);
  }

}
