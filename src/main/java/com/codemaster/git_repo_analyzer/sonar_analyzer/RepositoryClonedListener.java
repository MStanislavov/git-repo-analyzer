package com.codemaster.git_repo_analyzer.sonar_analyzer;

import static com.codemaster.git_repo_analyzer.util.Constants.*;

import com.codemaster.git_repo_analyzer.event.EventStatus;
import com.codemaster.git_repo_analyzer.event.EventType;
import com.codemaster.git_repo_analyzer.event.RepositoryAnalyzedEvent;
import com.codemaster.git_repo_analyzer.event.RepositoryClonedEvent;
import com.codemaster.git_repo_analyzer.persistence.RepositoryAnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
class RepositoryClonedListener {

  private static final Logger logger = LoggerFactory.getLogger(RepositoryClonedListener.class);

  private final ApplicationEventPublisher eventPublisher;
  private final MavenTemplateHandler mavenTemplateHandler;
  private final RepositoryAnalysisRepository analysisRepository;
  private final Semaphore sonarAnalysisSemaphore;

  RepositoryClonedListener(ApplicationEventPublisher eventPublisher,
      MavenTemplateHandler mavenTemplateHandler,
      RepositoryAnalysisRepository analysisRepository,
      @Qualifier("sonarAnalysisSemaphore") Semaphore sonarAnalysisSemaphore) {
    this.eventPublisher = eventPublisher;
    this.mavenTemplateHandler = mavenTemplateHandler;
    this.analysisRepository = analysisRepository;
    this.sonarAnalysisSemaphore = sonarAnalysisSemaphore;
  }

  @Async
  @EventListener
  public void onRepositoryCloned(RepositoryClonedEvent event) {
    if (event.getEventStatus().equals(EventStatus.FAILED)) {
      updateAnalysisStatus(event.getLocalRepositoryPath(), STATUS_FAILED, null, "Cloning failed");
      publishRepositoryAnalyzedEvent(event, "", EventStatus.FAILED);
    } else if (event.getEventStatus().equals(EventStatus.SUCCEEDED)) {
      executeShellProcess(event);
    }
  }

  private void executeShellProcess(RepositoryClonedEvent clonedEvent) {
    String repositoryPath = clonedEvent.getLocalRepositoryPath();
    try {
      // Step 1: Build (runs freely — not SonarQube-bound)
      updateAnalysisStatus(repositoryPath, STATUS_IN_PROGRESS, STEP_BUILDING, null);
      publishRepositoryAnalyzedEvent(clonedEvent, "", EventStatus.IN_PROGRESS);

      ShellProcessData buildData = mavenTemplateHandler.getBuildProcessData();
      int buildExitCode = runMavenProcess(repositoryPath, buildData, "mvn_build_output.log", "mvn_build_error.log");

      if (buildExitCode != 0) {
        logger.error("Maven build failed with exit code: {}", buildExitCode);
        updateAnalysisStatus(repositoryPath, STATUS_FAILED, STEP_BUILDING, "Maven build failed with exit code: " + buildExitCode);
        publishRepositoryAnalyzedEvent(clonedEvent, "", EventStatus.FAILED);
        return;
      }
      logger.info("Maven build succeeded for: {}", repositoryPath);

      // Step 2: Sonar analysis (throttled to max concurrent)
      updateAnalysisStatus(repositoryPath, STATUS_IN_PROGRESS, STEP_ANALYZING, null);

      logger.info("Acquiring sonar analysis permit (available: {})", sonarAnalysisSemaphore.availablePermits());
      sonarAnalysisSemaphore.acquire();
      try {
        ShellProcessData sonarData = mavenTemplateHandler.getSonarProcessData();
        File sonarLogFile = new File(repositoryPath, "mvn_sonar_output.log");
        int sonarExitCode = runMavenProcess(repositoryPath, sonarData, "mvn_sonar_output.log", "mvn_sonar_error.log");

        logger.info("Maven sonar process exited with code: {}", sonarExitCode);

        String projectKey = extractProjectKeyFromLogFile(sonarLogFile);
        if (projectKey != null) {
          updateAnalysisStatus(repositoryPath, STATUS_IN_PROGRESS, STEP_COLLECTING, null);
          updateProjectKey(repositoryPath, projectKey);
          publishRepositoryAnalyzedEvent(clonedEvent, projectKey, EventStatus.SUCCEEDED);
        } else {
          updateAnalysisStatus(repositoryPath, STATUS_FAILED, STEP_ANALYZING, "Could not extract project key from sonar output");
          publishRepositoryAnalyzedEvent(clonedEvent, "", EventStatus.FAILED);
        }
      } finally {
        sonarAnalysisSemaphore.release();
        logger.info("Released sonar analysis permit (available: {})", sonarAnalysisSemaphore.availablePermits());
      }
    } catch (InterruptedException e) {
      logger.error("Analysis interrupted for: {}", repositoryPath, e);
      Thread.currentThread().interrupt();
      updateAnalysisStatus(repositoryPath, STATUS_FAILED, null, "Analysis interrupted");
      publishRepositoryAnalyzedEvent(clonedEvent, "", EventStatus.FAILED);
    } catch (Exception e) {
      logger.error("Analysis process failed for: {}", repositoryPath, e);
      updateAnalysisStatus(repositoryPath, STATUS_FAILED, null, e.getMessage());
      publishRepositoryAnalyzedEvent(clonedEvent, "", EventStatus.FAILED);
    }
  }

  private int runMavenProcess(String repositoryPath, ShellProcessData shellProcessData,
      String outputLogName, String errorLogName) throws IOException, InterruptedException {
    List<String> commands = createProcessCommands(shellProcessData);
    ProcessBuilder processBuilder = new ProcessBuilder(commands);
    processBuilder.directory(new File(repositoryPath));

    File logFile = new File(repositoryPath, outputLogName);
    File errorFile = new File(repositoryPath, errorLogName);
    processBuilder.redirectOutput(logFile);
    processBuilder.redirectError(errorFile);

    Process process = processBuilder.start();
    return process.waitFor();
  }

  private static List<String> createProcessCommands(ShellProcessData shellProcessData) {
    List<String> commands = new ArrayList<>();
    if (shellProcessData.osType().equals(OS_TYPE.WINDOWS)) {
      commands.add("cmd.exe");
      commands.add("/c");
    } else {
      commands.add("/bin/sh");
      commands.add("-c");
    }
    commands.add(shellProcessData.mvnCommand());
    return commands;
  }

  private String extractProjectKeyFromLogFile(File logFile) {
    try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        logger.info(line);
        if (line.contains("Project key: ")) {
          return line.split(": ")[1].trim();
        }
      }
    } catch (IOException e) {
      logger.error("Error reading log file: {}", logFile.getAbsolutePath(), e);
    }
    return null;
  }

  private void updateAnalysisStatus(String repositoryPath, String status, String step, String errorMessage) {
    String repoName = extractRepoName(repositoryPath);
    analysisRepository.findAll().stream()
        .filter(e -> repoName.equals(e.getRepositoryName()))
        .findFirst()
        .ifPresent(entity -> {
          entity.setAnalysisStatus(status);
          if (step != null) {
            entity.setCurrentStep(step);
          }
          if (errorMessage != null) {
            entity.setErrorMessage(errorMessage);
          }
          analysisRepository.save(entity);
        });
  }

  private void updateProjectKey(String repositoryPath, String projectKey) {
    String repoName = extractRepoName(repositoryPath);
    analysisRepository.findAll().stream()
        .filter(e -> repoName.equals(e.getRepositoryName()))
        .findFirst()
        .ifPresent(entity -> {
          entity.setProjectKey(projectKey);
          analysisRepository.save(entity);
        });
  }

  private String extractRepoName(String repositoryPath) {
    Path path = Paths.get(repositoryPath);
    return path.getFileName().toString();
  }

  private void publishRepositoryAnalyzedEvent(RepositoryClonedEvent event, String projectKey, EventStatus status) {
    RepositoryAnalyzedEvent repositoryAnalyzedEvent = new RepositoryAnalyzedEvent(
        this,
        EventType.ANALYZED,
        status.equals(EventStatus.FAILED) ? "could not analyze repository" : "successfully analyzed",
        status,
        Timestamp.from(Instant.now()),
        UUID.randomUUID(),
        event.getJobId(),
        projectKey
    );
    eventPublisher.publishEvent(repositoryAnalyzedEvent);
  }

}
