package com.codemaster.git_repo_analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalysisPipelineE2ETest {

  private static final String CLONE_DIR =
      "C:\\Users\\cpthe\\work\\personal-projects\\self-branding\\git-repo-analyzer\\cloned";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  static Network network = Network.newNetwork();

  static PostgreSQLContainer<?> sonarDb = new PostgreSQLContainer<>("postgres:16-alpine")
      .withNetwork(network)
      .withNetworkAliases("db")
      .withDatabaseName("sonarqube")
      .withUsername("sonarqube")
      .withPassword("sonarqube");

  @SuppressWarnings("resource")
  static GenericContainer<?> sonarqube = new GenericContainer<>("sonarqube:10-community")
      .withNetwork(network)
      .withNetworkAliases("sonarqube")
      .withExposedPorts(9000)
      .withEnv("SONAR_JDBC_URL", "jdbc:postgresql://db:5432/sonarqube")
      .withEnv("SONAR_JDBC_USERNAME", "sonarqube")
      .withEnv("SONAR_JDBC_PASSWORD", "sonarqube")
      .waitingFor(Wait.forLogMessage(".*SonarQube is operational.*", 1)
          .withStartupTimeout(Duration.ofMinutes(5)));

  static PostgreSQLContainer<?> appDb = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("git_repo_analyzer")
      .withUsername("analyzer")
      .withPassword("analyzer");

  static String sonarToken;

  static {
    sonarDb.start();
    sonarqube.start();
    appDb.start();
    sonarToken = generateSonarToken();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", appDb::getJdbcUrl);
    registry.add("spring.datasource.username", appDb::getUsername);
    registry.add("spring.datasource.password", appDb::getPassword);
    registry.add("sonarqube-url", () -> "http://localhost:" + sonarqube.getMappedPort(9000));
    registry.add("sonarqube-scanner-url", () -> "http://sonarqube:9000");
    registry.add("sonarqube-auth", () -> sonarToken);
    registry.add("docker-network", network::getId);
    registry.add("clone-target-directory", () -> CLONE_DIR);
  }

  @LocalServerPort
  private int port;

  @Test
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  void analyzeStratoseer_succeeds() throws Exception {
    submitAnalysis("https://github.com/MStanislavov/stratoseer.git");
    JsonNode result = pollUntilTerminal("stratoseer", Duration.ofMinutes(8));

    assertThat(result.get("analysisStatus").asText()).isEqualTo("SUCCEEDED");
    assertThat(result.get("projectKey").asText()).isEqualTo("stratoseer");
    assertThat(result.get("ncloc").asInt()).isGreaterThan(0);
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  void analyzeGitRepoAnalyzer_succeeds() throws Exception {
    submitAnalysis("https://github.com/MStanislavov/git-repo-analyzer.git");
    JsonNode result = pollUntilTerminal("git-repo-analyzer", Duration.ofMinutes(8));

    assertThat(result.get("analysisStatus").asText()).isEqualTo("SUCCEEDED");
    assertThat(result.get("projectKey").asText()).isEqualTo("git-repo-analyzer");
    assertThat(result.get("ncloc").asInt()).isGreaterThan(0);
  }

  private void submitAnalysis(String repoUrl) throws Exception {
    String json = MAPPER.writeValueAsString(
        java.util.Map.of("url", repoUrl, "cloneDirectory", CLONE_DIR));
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + "/api/v1/analyze/url"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  private JsonNode pollUntilTerminal(String repoName, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("http://localhost:" + port + "/api/v1/results/" + repoName))
          .GET()
          .build();
      HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        JsonNode node = MAPPER.readTree(response.body());
        String status = node.get("analysisStatus").asText();
        if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
          return node;
        }
      }
      Thread.sleep(5000);
    }
    throw new AssertionError("Timed out waiting for analysis of " + repoName);
  }

  private static String generateSonarToken() {
    try {
      String sonarUrl = "http://localhost:" + sonarqube.getMappedPort(9000);
      String auth = Base64.getEncoder().encodeToString("admin:admin".getBytes());
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(sonarUrl + "/api/user_tokens/generate"))
          .header("Authorization", "Basic " + auth)
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString("name=e2e-test-" + System.currentTimeMillis()))
          .build();
      HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      String body = response.body();
      int tokenStart = body.indexOf("\"token\":\"") + 9;
      int tokenEnd = body.indexOf("\"", tokenStart);
      return body.substring(tokenStart, tokenEnd);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate SonarQube token", e);
    }
  }

}
