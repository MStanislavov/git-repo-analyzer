package com.codemaster.git_repo_analyzer.scraper;

public record RepositoryInfo(String repoUrl) {

  public String repoName() {
    return repoUrl.substring(repoUrl.lastIndexOf('/') + 1, repoUrl.lastIndexOf('.'));
  }

}
