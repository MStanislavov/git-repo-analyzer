package com.codemaster.git_repo_analyzer.persistence;

import com.codemaster.git_repo_analyzer.event.EventStatus;
import com.codemaster.git_repo_analyzer.event.EventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public final class ApplicationEventEntity {

  @Id
  private UUID id;

  @Enumerated(EnumType.STRING)
  private EventType eventType;

  private String message;

  @Enumerated(EnumType.STRING)
  private EventStatus eventStatus;

  private Timestamp eventTimestamp;

  private int jobId;

  @ManyToOne
  private ApplicationJobEntity applicationJob;

  @PrePersist
  public void generateUUID() {
    if (id == null) {
      id = UUID.randomUUID();
    }
  }

}
