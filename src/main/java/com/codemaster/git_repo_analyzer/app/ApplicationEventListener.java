package com.codemaster.git_repo_analyzer.app;

import static com.codemaster.git_repo_analyzer.util.Constants.*;

import com.codemaster.git_repo_analyzer.event.ApplicationEventModule;
import com.codemaster.git_repo_analyzer.event.RepositoryClonedEvent;
import com.codemaster.git_repo_analyzer.persistence.ApplicationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;


@Service
public class ApplicationEventListener {

  private static final Logger logger = LoggerFactory.getLogger(ApplicationEventListener.class);

  private final ApplicationEventRepository eventRepository;
  private final SseProgressService sseProgressService;

  public ApplicationEventListener(ApplicationEventRepository eventRepository,
      SseProgressService sseProgressService) {
    this.eventRepository = eventRepository;
    this.sseProgressService = sseProgressService;
  }

  @Async
  @EventListener
  public void onApplicationEvent(ApplicationEventModule event) {
    logger.info("Received event: {}", event.getMessage());
    eventRepository.save(ApplicationEventMapper.toEntity(event));
    broadcastProgress(event);
  }

  private void broadcastProgress(ApplicationEventModule event) {
    String repoName = extractRepoName(event);
    String step = mapEventToStep(event);
    String status = event.getEventStatus().name();
    String message = event.getMessage();
    sseProgressService.broadcast(repoName, step, status, message);
  }

  private String extractRepoName(ApplicationEventModule event) {
    if (event instanceof RepositoryClonedEvent clonedEvent) {
      String path = clonedEvent.getLocalRepositoryPath();
      return Paths.get(path).getFileName().toString();
    }
    return event.getEventType().name();
  }

  private String mapEventToStep(ApplicationEventModule event) {
    return switch (event.getEventType()) {
      case CLONED -> STEP_CLONING;
      case ANALYZED -> STEP_ANALYZING;
      case DATA_COLLECTED -> STEP_COLLECTING;
    };
  }

}
