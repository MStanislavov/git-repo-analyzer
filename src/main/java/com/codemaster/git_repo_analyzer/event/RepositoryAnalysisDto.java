package com.codemaster.git_repo_analyzer.event;

import java.sql.Timestamp;

public record RepositoryAnalysisDto(
    Long id,
    String repositoryUrl,
    String repositoryName,
    String projectKey,
    Integer ncloc,
    Integer bugs,
    Integer vulnerabilities,
    Integer codeSmells,
    Integer sqaleIndex,
    Double coverage,
    Double duplicatedLinesDensity,
    Double reliabilityRating,
    Double securityRating,
    Double sqaleRating,
    String alertStatus,
    String analysisStatus,
    String currentStep,
    String errorMessage,
    Timestamp analyzedAt
) {
}
