package com.codemaster.git_repo_analyzer.event;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
public abstract class ApplicationEventModule extends ApplicationEvent {

  private final EventType eventType;

  @Setter
  private String message;

  @Setter
  private EventStatus eventStatus;

  private final Timestamp eventTimestamp;

  private final UUID uuid;

  private final int jobId;

  private final String repositoryName;

  protected ApplicationEventModule(
      Object source,
      EventType eventType,
      String message,
      EventStatus eventStatus,
      Timestamp eventTimestamp,
      UUID uuid,
      int jobId,
      String repositoryName) {
    super(source);
    this.eventType = eventType;
    this.message = message;
    this.eventStatus = eventStatus;
    this.eventTimestamp = eventTimestamp;
    this.uuid = uuid;
    this.jobId = jobId;
    this.repositoryName = repositoryName;
  }



}
