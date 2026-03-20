package com.codemaster.git_repo_analyzer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositoryAnalysisRepository extends JpaRepository<RepositoryAnalysisEntity, Long> {

  Optional<RepositoryAnalysisEntity> findByRepositoryUrl(String repositoryUrl);

  long countByJobId(Integer jobId);

}
