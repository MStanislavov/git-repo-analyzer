package com.codemaster.git_repo_analyzer.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class RepositoryAnalysisEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true)
  private String repositoryUrl;

  private String repositoryName;

  private Integer jobId;

  private String projectKey;

  private Integer ncloc;

  private Integer bugs;

  private Integer vulnerabilities;

  private Integer codeSmells;

  private Integer sqaleIndex;

  private Double coverage;

  private Double duplicatedLinesDensity;

  private Double reliabilityRating;

  private Double securityRating;

  private Double sqaleRating;

  private String alertStatus;

  private String analysisStatus;

  private String currentStep;

  private String errorMessage;

  private Timestamp analyzedAt;

}
