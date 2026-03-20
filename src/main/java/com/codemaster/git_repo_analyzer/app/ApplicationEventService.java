package com.codemaster.git_repo_analyzer.app;

import com.codemaster.git_repo_analyzer.event.DebtDataDto;
import com.codemaster.git_repo_analyzer.persistence.ApplicationJobRepository;
import org.springframework.stereotype.Service;

@Service
class ApplicationEventService {

  private final ApplicationJobRepository applicationJobRepository;

  ApplicationEventService(ApplicationJobRepository applicationJobRepository) {
    this.applicationJobRepository = applicationJobRepository;
  }

  DebtDataDto getLatestProjectDebtInformation() {
      //TODO
      return null;
  }
}
