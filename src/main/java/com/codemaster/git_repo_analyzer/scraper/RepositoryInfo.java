package com.codemaster.git_repo_analyzer.scraper;

import java.nio.file.Paths;

public record RepositoryInfo(String repoUrl, boolean localPath) {

  public RepositoryInfo(String repoUrl) {
    this(repoUrl, false);
  }

  public String repoName() {
    if (localPath) {
      return Paths.get(repoUrl).getFileName().toString();
    }
    return repoUrl.substring(repoUrl.lastIndexOf('/') + 1, repoUrl.lastIndexOf('.'));
  }

}
