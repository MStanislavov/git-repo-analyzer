package com.codemaster.git_repo_analyzer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationJobRepository extends JpaRepository<ApplicationJobEntity, Integer> {

}
