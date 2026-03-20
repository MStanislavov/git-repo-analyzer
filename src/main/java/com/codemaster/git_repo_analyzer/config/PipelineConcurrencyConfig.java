package com.codemaster.git_repo_analyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Semaphore;

@Configuration
@EnableAsync
public class PipelineConcurrencyConfig {

  @Bean
  public Semaphore cloneSemaphore(
      @Value("${pipeline.clone.max-concurrent:10}") int maxConcurrent) {
    return new Semaphore(maxConcurrent, true);
  }

  @Bean
  public Semaphore sonarAnalysisSemaphore(
      @Value("${pipeline.sonar-analysis.max-concurrent:5}") int maxConcurrent) {
    return new Semaphore(maxConcurrent, true);
  }

}
