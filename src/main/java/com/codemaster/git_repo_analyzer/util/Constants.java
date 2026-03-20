package com.codemaster.git_repo_analyzer.util;

public final class Constants {

  private Constants() {
    throw new IllegalStateException("Cannot instantiate constants class");
  }

  // Analysis status
  public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_SUCCEEDED = "SUCCEEDED";

  // Pipeline steps
  public static final String STEP_CLONING = "CLONING";
  public static final String STEP_BUILDING = "BUILDING";
  public static final String STEP_ANALYZING = "ANALYZING";
  public static final String STEP_COLLECTING = "COLLECTING";
  public static final String STEP_COMPLETED = "COMPLETED";

}
