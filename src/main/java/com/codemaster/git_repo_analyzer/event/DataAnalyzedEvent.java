package com.codemaster.git_repo_analyzer.event;

import lombok.Getter;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
public final class DataAnalyzedEvent extends ApplicationEventModule {

  public DataAnalyzedEvent(Object source, EventType eventType, String message, EventStatus eventStatus, Timestamp eventTimestamp,
      UUID uuid, int jobId, String repositoryName) {
    super(source, eventType, message, eventStatus, eventTimestamp, uuid, jobId, repositoryName);
  }

}
