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

import java.io.File;
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
  private final DockerScannerCommandBuilder dockerScannerCommandBuilder;
  private final RepositoryAnalysisRepository analysisRepository;
  private final Semaphore sonarAnalysisSemaphore;

  RepositoryClonedListener(ApplicationEventPublisher eventPublisher,
      DockerScannerCommandBuilder dockerScannerCommandBuilder,
      RepositoryAnalysisRepository analysisRepository,
      @Qualifier("sonarAnalysisSemaphore") Semaphore sonarAnalysisSemaphore) {
    this.eventPublisher = eventPublisher;
    this.dockerScannerCommandBuilder = dockerScannerCommandBuilder;
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
      updateAnalysisStatus(repositoryPath, STATUS_IN_PROGRESS, STEP_ANALYZING, null);

      String projectKey = extractRepoName(repositoryPath);
      publishRepositoryAnalyzedEvent(clonedEvent, projectKey, EventStatus.IN_PROGRESS);

      ShellProcessData scannerData = dockerScannerCommandBuilder.buildCommand(repositoryPath, projectKey);

      logger.info("Acquiring sonar analysis permit (available: {})", sonarAnalysisSemaphore.availablePermits());
      sonarAnalysisSemaphore.acquire();
      try {
        int exitCode = runProcess(repositoryPath, scannerData, "sonar_scanner_output.log", "sonar_scanner_error.log");
        logger.info("Docker sonar-scanner process exited with code: {}", exitCode);

        if (exitCode == 0) {
          updateAnalysisStatus(repositoryPath, STATUS_IN_PROGRESS, STEP_COLLECTING, null);
          updateProjectKey(repositoryPath, projectKey);
          publishRepositoryAnalyzedEvent(clonedEvent, projectKey, EventStatus.SUCCEEDED);
        } else {
          updateAnalysisStatus(repositoryPath, STATUS_FAILED, STEP_ANALYZING, "Sonar scanner failed with exit code: " + exitCode);
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

  private int runProcess(String repositoryPath, ShellProcessData shellProcessData,
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
    commands.add(shellProcessData.command());
    return commands;
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
    String message = switch (status) {
      case FAILED -> "could not analyze repository";
      case IN_PROGRESS -> "analyzing repository";
      case SUCCEEDED -> "successfully analyzed";
    };
    RepositoryAnalyzedEvent repositoryAnalyzedEvent = new RepositoryAnalyzedEvent(
        this,
        EventType.ANALYZED,
        message,
        status,
        Timestamp.from(Instant.now()),
        UUID.randomUUID(),
        event.getJobId(),
        projectKey
    );
    eventPublisher.publishEvent(repositoryAnalyzedEvent);
  }

}
