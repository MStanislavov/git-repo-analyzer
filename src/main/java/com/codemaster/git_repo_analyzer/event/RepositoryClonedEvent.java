package com.codemaster.git_repo_analyzer.event;

import lombok.Getter;

import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.UUID;

@Getter
public final class RepositoryClonedEvent extends ApplicationEventModule {

  private final String localRepositoryPath;

  public RepositoryClonedEvent(Object source, EventType eventType, String message, EventStatus eventStatus, Timestamp eventTimestamp,
      UUID uuid, int jobId, String localRepositoryPath) {
    super(source, eventType, message, eventStatus, eventTimestamp, uuid, jobId,
        Paths.get(localRepositoryPath).getFileName().toString());
    this.localRepositoryPath = localRepositoryPath;
  }
}
