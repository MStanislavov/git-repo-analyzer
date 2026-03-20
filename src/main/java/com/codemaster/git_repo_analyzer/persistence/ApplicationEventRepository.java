package com.codemaster.git_repo_analyzer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ApplicationEventRepository extends JpaRepository<ApplicationEventEntity, Integer> {

  @Transactional
  void deleteByJobId(int jobId);

}
