package com.codemaster.git_repo_analyzer.sonar_analyzer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerScannerCommandBuilderTest {

  private final DockerScannerCommandBuilder builder =
      new DockerScannerCommandBuilder("http://localhost:9000", "test-token", "");

  @Test
  void buildCommand_containsDockerRunWithSonarScannerImage() {
    ShellProcessData data = builder.buildCommand("/repos/my-repo", "my-repo");

    assertThat(data.command()).contains("docker run --rm");
    assertThat(data.command()).contains("sonarsource/sonar-scanner-cli");
  }

  @Test
  void buildCommand_mountsRepoVolumeToUsrSrc() {
    ShellProcessData data = builder.buildCommand("/repos/my-repo", "my-repo");

    assertThat(data.command()).contains("-v \"/repos/my-repo:/usr/src\"");
  }

  @Test
  void buildCommand_setsProjectKeyAndName() {
    ShellProcessData data = builder.buildCommand("/repos/my-repo", "my-project");

    assertThat(data.command()).contains("-Dsonar.projectKey=my-project");
    assertThat(data.command()).contains("-Dsonar.projectName=my-project");
  }

  @Test
  void buildCommand_setsSourcesToUsrSrc() {
    ShellProcessData data = builder.buildCommand("/repos/my-repo", "my-repo");

    assertThat(data.command()).contains("-Dsonar.sources=/usr/src");
  }

  @Test
  void buildCommand_setsHostUrlAndToken() {
    ShellProcessData data = builder.buildCommand("/repos/my-repo", "my-repo");

    assertThat(data.command()).contains("-Dsonar.host.url=http://localhost:9000");
    assertThat(data.command()).contains("-Dsonar.token=test-token");
  }

  @Test
  void buildCommand_normalizesWindowsBackslashesToForwardSlashes() {
    ShellProcessData data = builder.buildCommand("C:\\Users\\test\\repos\\my-repo", "my-repo");

    assertThat(data.command()).contains("-v \"C:/Users/test/repos/my-repo:/usr/src\"");
    assertThat(data.command()).doesNotContain("\\");
  }

  @Test
  void buildCommand_detectsOsType() {
    ShellProcessData data = builder.buildCommand("/repos/my-repo", "my-repo");

    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    OS_TYPE expected = isWindows ? OS_TYPE.WINDOWS : OS_TYPE.UNIX;
    assertThat(data.osType()).isEqualTo(expected);
  }

  @Test
  void buildCommand_differentUrlsAndTokensAreReflectedInCommand() {
    DockerScannerCommandBuilder custom =
        new DockerScannerCommandBuilder("http://host.docker.internal:9000", "prod-token-123", "");

    ShellProcessData data = custom.buildCommand("/repos/app", "app");

    assertThat(data.command()).contains("-Dsonar.host.url=http://host.docker.internal:9000");
    assertThat(data.command()).contains("-Dsonar.token=prod-token-123");
  }

  @Test
  void buildCommand_withoutNetwork_noNetworkFlag() {
    ShellProcessData data = builder.buildCommand("/repos/my-repo", "my-repo");

    assertThat(data.command()).doesNotContain("--network");
  }

  @Test
  void buildCommand_withNetwork_includesNetworkFlag() {
    DockerScannerCommandBuilder withNetwork =
        new DockerScannerCommandBuilder("http://sonarqube:9000", "token", "git-repo-analyzer_default");

    ShellProcessData data = withNetwork.buildCommand("/repos/my-repo", "my-repo");

    assertThat(data.command()).contains("--network git-repo-analyzer_default");
  }

}
