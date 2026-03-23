package com.codemaster.git_repo_analyzer.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantsTest {

  @Test
  void stepBuildingConstantDoesNotExist() {
    List<String> fieldNames = Arrays.stream(Constants.class.getDeclaredFields())
        .map(Field::getName)
        .toList();

    assertThat(fieldNames).doesNotContain("STEP_BUILDING");
  }

  @Test
  void pipelineStepsExist() {
    assertThat(Constants.STEP_CLONING).isEqualTo("CLONING");
    assertThat(Constants.STEP_ANALYZING).isEqualTo("ANALYZING");
    assertThat(Constants.STEP_COLLECTING).isEqualTo("COLLECTING");
    assertThat(Constants.STEP_COMPLETED).isEqualTo("COMPLETED");
  }

  @Test
  void statusConstantsExist() {
    assertThat(Constants.STATUS_IN_PROGRESS).isEqualTo("IN_PROGRESS");
    assertThat(Constants.STATUS_FAILED).isEqualTo("FAILED");
    assertThat(Constants.STATUS_SUCCEEDED).isEqualTo("SUCCEEDED");
  }

}
