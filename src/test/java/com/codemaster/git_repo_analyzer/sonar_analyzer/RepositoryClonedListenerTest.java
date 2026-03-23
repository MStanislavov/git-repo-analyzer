package com.codemaster.git_repo_analyzer.sonar_analyzer;

import com.codemaster.git_repo_analyzer.event.EventStatus;
import com.codemaster.git_repo_analyzer.event.EventType;
import com.codemaster.git_repo_analyzer.event.RepositoryAnalyzedEvent;
import com.codemaster.git_repo_analyzer.event.RepositoryClonedEvent;
import com.codemaster.git_repo_analyzer.persistence.RepositoryAnalysisEntity;
import com.codemaster.git_repo_analyzer.persistence.RepositoryAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositoryClonedListenerTest {

  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private DockerScannerCommandBuilder dockerScannerCommandBuilder;
  @Mock private RepositoryAnalysisRepository analysisRepository;

  private final Semaphore sonarAnalysisSemaphore = new Semaphore(5);
  private RepositoryClonedListener listener;

  @BeforeEach
  void setUp() {
    listener = new RepositoryClonedListener(
        eventPublisher, dockerScannerCommandBuilder, analysisRepository, sonarAnalysisSemaphore);
  }

  // --- Failed clone event ---

  @Test
  void failedCloneEvent_publishesFailedAnalyzedEvent() {
    RepositoryAnalysisEntity entity = createEntity("my-repo");
    when(analysisRepository.findAll()).thenReturn(List.of(entity));

    RepositoryClonedEvent event = createClonedEvent(EventStatus.FAILED, "/repos/my-repo");
    listener.onRepositoryCloned(event);

    ArgumentCaptor<RepositoryAnalyzedEvent> captor = ArgumentCaptor.forClass(RepositoryAnalyzedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().getEventStatus()).isEqualTo(EventStatus.FAILED);
  }

  @Test
  void failedCloneEvent_updatesEntityStatusToFailed() {
    RepositoryAnalysisEntity entity = createEntity("my-repo");
    when(analysisRepository.findAll()).thenReturn(List.of(entity));

    RepositoryClonedEvent event = createClonedEvent(EventStatus.FAILED, "/repos/my-repo");
    listener.onRepositoryCloned(event);

    verify(analysisRepository).save(entity);
    assertThat(entity.getAnalysisStatus()).isEqualTo("FAILED");
    assertThat(entity.getErrorMessage()).isEqualTo("Cloning failed");
  }

  @Test
  void failedCloneEvent_doesNotInvokeDockerCommandBuilder() {
    RepositoryAnalysisEntity entity = createEntity("my-repo");
    when(analysisRepository.findAll()).thenReturn(List.of(entity));

    RepositoryClonedEvent event = createClonedEvent(EventStatus.FAILED, "/repos/my-repo");
    listener.onRepositoryCloned(event);

    verifyNoInteractions(dockerScannerCommandBuilder);
  }

  // --- Succeeded clone event — process succeeds ---

  @Test
  void succeededCloneEvent_invokesDockerCommandBuilder(@TempDir Path tempDir) throws Exception {
    Path repoDir = Files.createDirectory(tempDir.resolve("my-repo"));
    String repoPath = repoDir.toString();
    RepositoryAnalysisEntity entity = createEntity("my-repo");
    when(analysisRepository.findAll()).thenReturn(List.of(entity));
    when(dockerScannerCommandBuilder.buildCommand(repoPath, "my-repo"))
        .thenReturn(echoCommand("scanner-ok"));

    RepositoryClonedEvent event = createClonedEvent(EventStatus.SUCCEEDED, repoPath);
    listener.onRepositoryCloned(event);

    verify(dockerScannerCommandBuilder).buildCommand(repoPath, "my-repo");
  }

  @Test
  void succeededCloneEvent_processSucceeds_publishesSucceededEvent(@TempDir Path tempDir) throws Exception {
    Path repoDir = Files.createDirectory(tempDir.resolve("my-repo"));
    String repoPath = repoDir.toString();
    RepositoryAnalysisEntity entity = createEntity("my-repo");
    when(analysisRepository.findAll()).thenReturn(List.of(entity));
    when(dockerScannerCommandBuilder.buildCommand(repoPath, "my-repo"))
        .thenReturn(echoCommand("scanner-ok"));

    RepositoryClonedEvent event = createClonedEvent(EventStatus.SUCCEEDED, repoPath);
    listener.onRepositoryCloned(event);

    ArgumentCaptor<RepositoryAnalyzedEvent> captor = ArgumentCaptor.forClass(RepositoryAnalyzedEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
    RepositoryAnalyzedEvent last = captor.getAllValues().getLast();
    assertThat(last.getEventStatus()).isEqualTo(EventStatus.SUCCEEDED);
    assertThat(last.getProjectKey()).isEqualTo("my-repo");
  }

  @Test
  void succeededCloneEvent_processSucceeds_updatesProjectKey(@TempDir Path tempDir) throws Exception {
    Path repoDir = Files.createDirectory(tempDir.resolve("my-repo"));
    String repoPath = repoDir.toString();
    RepositoryAnalysisEntity entity = createEntity("my-repo");
    when(analysisRepository.findAll()).thenReturn(List.of(entity));
    when(dockerScannerCommandBuilder.buildCommand(repoPath, "my-repo"))
        .thenReturn(echoCommand("scanner-ok"));

    RepositoryClonedEvent event = createClonedEvent(EventStatus.SUCCEEDED, repoPath);
    listener.onRepositoryCloned(event);

    assertThat(entity.getProjectKey()).isEqualTo("my-repo");
  }

  @Test
  void succeededCloneEvent_processSucceeds_setsStepToCollecting(@TempDir Path tempDir) throws Exception {
    Path repoDir = Files.createDirectory(tempDir.resolve("my-repo"));
    String repoPath = repoDir.toString();
    RepositoryAnalysisEntity entity = createEntity("my-repo");
    when(analysisRepository.findAll()).thenReturn(List.of(entity));
    when(dockerScannerCommandBuilder.buildCommand(repoPath, "my-repo"))
        .thenReturn(echoCommand("scanner-ok"));

    RepositoryClonedEvent event = createClonedEvent(EventStatus.SUCCEEDED, repoPath);
    listener.onRepositoryCloned(event);

    assertThat(entity.getCurrentStep()).isEqualTo("COLLECTING");
  }

  // --- Succeeded clone event — process fails ---

  @Test
  void succeededCloneEvent_processFails_publishesFailedEvent(@TempDir Path tempDir) throws Exception {
    Path repoDir = Files.createDirectory(tempDir.resolve("my-repo"));
    String repoPath = repoDir.toString();
    RepositoryAnalysisEntity entity = createEntity("my-repo");
    when(analysisRepository.findAll()).thenReturn(List.of(entity));
    when(dockerScannerCommandBuilder.buildCommand(repoPath, "my-repo"))
        .thenReturn(failingCommand());

    RepositoryClonedEvent event = createClonedEvent(EventStatus.SUCCEEDED, repoPath);
    listener.onRepositoryCloned(event);

    ArgumentCaptor<RepositoryAnalyzedEvent> captor = ArgumentCaptor.forClass(RepositoryAnalyzedEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
    RepositoryAnalyzedEvent last = captor.getAllValues().getLast();
    assertThat(last.getEventStatus()).isEqualTo(EventStatus.FAILED);
  }

  @Test
  void succeededCloneEvent_processFails_updatesStatusToFailed(@TempDir Path tempDir) throws Exception {
    Path repoDir = Files.createDirectory(tempDir.resolve("my-repo"));
    String repoPath = repoDir.toString();
    RepositoryAnalysisEntity entity = createEntity("my-repo");
    when(analysisRepository.findAll()).thenReturn(List.of(entity));
    when(dockerScannerCommandBuilder.buildCommand(repoPath, "my-repo"))
        .thenReturn(failingCommand());

    RepositoryClonedEvent event = createClonedEvent(EventStatus.SUCCEEDED, repoPath);
    listener.onRepositoryCloned(event);

    assertThat(entity.getAnalysisStatus()).isEqualTo("FAILED");
    assertThat(entity.getErrorMessage()).startsWith("Sonar scanner failed with exit code:");
  }

  // --- Semaphore ---

  @Test
  void succeededCloneEvent_releasesSemaphoreAfterProcessCompletes(@TempDir Path tempDir) throws Exception {
    Path repoDir = Files.createDirectory(tempDir.resolve("my-repo"));
    String repoPath = repoDir.toString();
    RepositoryAnalysisEntity entity = createEntity("my-repo");
    when(analysisRepository.findAll()).thenReturn(List.of(entity));
    when(dockerScannerCommandBuilder.buildCommand(repoPath, "my-repo"))
        .thenReturn(echoCommand("scanner-ok"));

    int permitsBefore = sonarAnalysisSemaphore.availablePermits();

    RepositoryClonedEvent event = createClonedEvent(EventStatus.SUCCEEDED, repoPath);
    listener.onRepositoryCloned(event);

    assertThat(sonarAnalysisSemaphore.availablePermits()).isEqualTo(permitsBefore);
  }

  // --- Helpers ---

  private RepositoryClonedEvent createClonedEvent(EventStatus status, String repoPath) {
    return new RepositoryClonedEvent(
        this, EventType.CLONED, "test", status,
        Timestamp.from(Instant.now()), UUID.randomUUID(), 1, repoPath);
  }

  private RepositoryAnalysisEntity createEntity(String repoName) {
    RepositoryAnalysisEntity entity = new RepositoryAnalysisEntity();
    entity.setRepositoryName(repoName);
    return entity;
  }

  private ShellProcessData echoCommand(String message) {
    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    OS_TYPE osType = isWindows ? OS_TYPE.WINDOWS : OS_TYPE.UNIX;
    return new ShellProcessData("echo " + message, osType);
  }

  private ShellProcessData failingCommand() {
    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    OS_TYPE osType = isWindows ? OS_TYPE.WINDOWS : OS_TYPE.UNIX;
    String command = isWindows ? "cmd.exe /c exit 1" : "exit 1";
    return new ShellProcessData(command, osType);
  }

}
