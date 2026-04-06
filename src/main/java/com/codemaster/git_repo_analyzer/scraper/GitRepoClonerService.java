package com.codemaster.git_repo_analyzer.scraper;

import com.codemaster.git_repo_analyzer.event.EventStatus;
import com.codemaster.git_repo_analyzer.event.EventType;
import com.codemaster.git_repo_analyzer.event.RepositoryClonedEvent;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
public final class GitRepoClonerService {

  private final Semaphore cloneSemaphore;

  private final ApplicationEventPublisher eventPublisher;

  private final String cloneTargetDirectory;

  private static final Logger logger = LoggerFactory.getLogger(GitRepoClonerService.class);


  public GitRepoClonerService(
      @Qualifier("cloneSemaphore") Semaphore cloneSemaphore,
      ApplicationEventPublisher eventPublisher,
      @Value("${clone-target-directory}") String cloneTargetDirectory) {
    this.cloneSemaphore = cloneSemaphore;
    this.eventPublisher = eventPublisher;
    this.cloneTargetDirectory = cloneTargetDirectory;
  }

  public void execute(int jobId, Set<RepositoryInfo> repositories, String cloneDirectory) {
    repositories.forEach(repoInfo ->
        Thread.startVirtualThread(() -> {
          if (repoInfo.localPath()) {
            validateAndPublishLocalRepo(jobId, repoInfo);
          } else {
            cloneRepository(jobId, repoInfo, cloneDirectory);
          }
        }));
  }

  private void validateAndPublishLocalRepo(int jobId, RepositoryInfo repoInfo) {
    String localPath = repoInfo.repoUrl();
    Path repoPath = Paths.get(localPath);
    Path gitDir = repoPath.resolve(".git");

    if (!Files.exists(repoPath) || !Files.isDirectory(repoPath)) {
      logger.error("Local path does not exist or is not a directory: {}", localPath);
      publishRepositoryClonedEvent(localPath, EventStatus.FAILED, jobId);
      return;
    }
    if (!Files.exists(gitDir)) {
      logger.error("Path is not a git repository (no .git directory): {}", localPath);
      publishRepositoryClonedEvent(localPath, EventStatus.FAILED, jobId);
      return;
    }

    logger.info("Local repository validated: {}", localPath);
    publishRepositoryClonedEvent(localPath, EventStatus.SUCCEEDED, jobId);
  }

  private void cloneRepository(int jobId, RepositoryInfo repoInfo, String cloneDirectory) {
    Path repoDirPath = Paths.get(cloneDirectory, repoInfo.repoName());
    publishRepositoryClonedEvent(repoDirPath.toString(), EventStatus.IN_PROGRESS, jobId);
    try {
      logger.info("Acquiring clone permit (available: {})", cloneSemaphore.availablePermits());
      cloneSemaphore.acquire();
      try {
        if (Files.exists(repoDirPath)) {
          logger.info("Folder already exists: {}. Beginning drop operation", repoDirPath);
          FileUtils.deleteDirectory(new File(repoDirPath.toString()));
        }
        Files.createDirectories(repoDirPath.getParent());
        List<String> command = List.of("git", "clone", repoInfo.repoUrl(), repoDirPath.toString());
        Process process = createProcess(command);
        int exitCode = process.waitFor();
        validateProcessState(repoDirPath.toString(), exitCode, jobId);
      } finally {
        cloneSemaphore.release();
        logger.info("Released clone permit (available: {})", cloneSemaphore.availablePermits());
      }
    } catch (InterruptedException e) {
      logger.error("Clone interrupted for: {}", repoDirPath, e);
      Thread.currentThread().interrupt();
      publishRepositoryClonedEvent(repoDirPath.toString(), EventStatus.FAILED, jobId);
    } catch (Exception e) {
      logger.error("Something went wrong while cloning: {}", repoDirPath, e);
      publishRepositoryClonedEvent(repoDirPath.toString(), EventStatus.FAILED, jobId);
    }
  }

  private void validateProcessState(String path, int exitCode, int jobId) {
    if (exitCode == 0) {
      logger.info("Cloned: {}", path);
      publishRepositoryClonedEvent(path, EventStatus.SUCCEEDED, jobId);
    } else {
      String errorMessage = "Failed to clone";
      logger.error("{} {}: Exit code {}", errorMessage, path, exitCode);
      throw new GitCloneException(errorMessage);
    }
  }

  private static Process createProcess(List<String> command) throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.inheritIO();
    return processBuilder.start();
  }

  private void publishRepositoryClonedEvent(String repoPath, EventStatus status, int jobId) {
    RepositoryClonedEvent repositoryClonedEvent = new RepositoryClonedEvent(
        this,
        EventType.CLONED,
        status.equals(EventStatus.FAILED) ? "could not clone" : "start cloning process",
        status,
        Timestamp.from(Instant.now()),
        UUID.randomUUID(),
        jobId,
        repoPath
    );
    eventPublisher.publishEvent(repositoryClonedEvent);
  }

}
