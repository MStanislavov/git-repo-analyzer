package com.codemaster.git_repo_analyzer.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.sql.Timestamp;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public final class ApplicationJobEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  private String jobName;

  private Timestamp startTime;

  private String jobMessage;

  @OneToMany(mappedBy = "applicationJob")
  private Set<ApplicationEventEntity> applicationEvents;

}
