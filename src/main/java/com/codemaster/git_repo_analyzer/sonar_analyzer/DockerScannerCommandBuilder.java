package com.codemaster.git_repo_analyzer.sonar_analyzer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class DockerScannerCommandBuilder {

  private final String scannerUrl;
  private final String authToken;
  private final String dockerNetwork;
  private final OS_TYPE osType;

  DockerScannerCommandBuilder(
      @Value("${sonarqube-scanner-url}") String scannerUrl,
      @Value("${sonarqube-auth}") String authToken,
      @Value("${docker-network:}") String dockerNetwork) {
    this.scannerUrl = scannerUrl;
    this.authToken = authToken;
    this.dockerNetwork = dockerNetwork;
    this.osType = System.getProperty("os.name").toLowerCase().contains("win")
        ? OS_TYPE.WINDOWS
        : OS_TYPE.UNIX;
  }

  ShellProcessData buildCommand(String repoPath, String projectKey) {
    String volumePath = repoPath.replace("\\", "/");
    String networkFlag = dockerNetwork != null && !dockerNetwork.isBlank()
        ? " --network " + dockerNetwork
        : "";
    String command = String.format(
        "docker run --rm%s -v \"%s:/usr/src\" sonarsource/sonar-scanner-cli"
            + " -Dsonar.projectKey=%s -Dsonar.projectName=%s"
            + " -Dsonar.sources=/usr/src"
            + " -Dsonar.java.binaries=/tmp"
            + " -Dsonar.host.url=%s -Dsonar.token=%s",
        networkFlag, volumePath, projectKey, projectKey, scannerUrl, authToken);
    return new ShellProcessData(command, osType);
  }

}
