package com.codemaster.git_repo_analyzer.event;


import lombok.Getter;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
public class RepositoryAnalyzedEvent extends ApplicationEventModule {

  private final String projectKey;

  public RepositoryAnalyzedEvent(Object source, EventType eventType, String message, EventStatus eventStatus, Timestamp eventTimestamp,
      UUID uuid, int jobId, String projectKey) {
    super(source, eventType, message, eventStatus, eventTimestamp, uuid, jobId, projectKey);
    this.projectKey = projectKey;
  }

}
