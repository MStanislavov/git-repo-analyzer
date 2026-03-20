package com.codemaster.git_repo_analyzer;

import com.codemaster.git_repo_analyzer.persistence.ApplicationJobEntity;
import com.codemaster.git_repo_analyzer.persistence.ApplicationJobRepository;
import com.codemaster.git_repo_analyzer.scraper.GitRepoClonerService;
import com.codemaster.git_repo_analyzer.scraper.RepositoryInfo;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Component
public class CloningEventScheduler {

  private final GitRepoClonerService gitRepoClonerService;

  private final ApplicationJobRepository jobRepository;

  public CloningEventScheduler(GitRepoClonerService gitRepoClonerService, ApplicationJobRepository jobRepository) {
    this.gitRepoClonerService = gitRepoClonerService;
    this.jobRepository = jobRepository;
  }

  public int createJob() {
    return jobRepository.save(new ApplicationJobEntity(
        null, "Data Analyzer",
        Timestamp.from(Instant.now()), "gathers data from the cloned repositories",
        new HashSet<>())).getId();
  }

  public void executeAnalysis(int jobId, Set<RepositoryInfo> repositories, String cloneDirectory) {
    gitRepoClonerService.execute(jobId, repositories, cloneDirectory);
  }

}
