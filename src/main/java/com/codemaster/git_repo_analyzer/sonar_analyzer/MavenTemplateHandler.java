package com.codemaster.git_repo_analyzer.sonar_analyzer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class MavenTemplateHandler {

  private static final String MVN_BUILD_COMMAND = "mvn clean install -DskipTests=true";

  private static final String MVN_SONAR_COMMAND_TEMPLATE =
      "mvn sonar:sonar -Dsonar.host.url=%s -Dsonar.login=%s -DskipTests=true";

  private final ShellProcessData buildProcessData;
  private final ShellProcessData sonarProcessData;

  public MavenTemplateHandler(
      @Value("${sonarqube-url}") String sonarqubeUrl,
      @Value("${sonarqube-auth}") String sonarqubeAuth) {

    OS_TYPE osType = determineOsType();
    String sonarCommand = String.format(MVN_SONAR_COMMAND_TEMPLATE, sonarqubeUrl, sonarqubeAuth);

    buildProcessData = new ShellProcessData(MVN_BUILD_COMMAND, osType);
    sonarProcessData = new ShellProcessData(sonarCommand, osType);
  }

  private static OS_TYPE determineOsType() {
    return System.getProperty("os.name").toLowerCase().contains("win")
        ? OS_TYPE.WINDOWS
        : OS_TYPE.UNIX;
  }

  public ShellProcessData getBuildProcessData() {
    return this.buildProcessData;
  }

  public ShellProcessData getSonarProcessData() {
    return this.sonarProcessData;
  }

}
