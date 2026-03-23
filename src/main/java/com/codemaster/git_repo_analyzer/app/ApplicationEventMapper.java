package com.codemaster.git_repo_analyzer.app;

import com.codemaster.git_repo_analyzer.event.ApplicationEventModule;
import com.codemaster.git_repo_analyzer.persistence.ApplicationEventEntity;

import java.util.Set;
import java.util.stream.Collectors;

final class ApplicationEventMapper {

  private ApplicationEventMapper() {
    throw new IllegalStateException("Can not instantiate this mapper utility class");
  }

  static ApplicationEventEntity toEntity(ApplicationEventModule dto) {
    ApplicationEventEntity entity = new ApplicationEventEntity();
    entity.setId(dto.getUuid());
    entity.setEventType(dto.getEventType());
    entity.setMessage(dto.getMessage());
    entity.setEventStatus(dto.getEventStatus());
    entity.setEventTimestamp(dto.getEventTimestamp());
    entity.setJobId(dto.getJobId());
    return entity;
  }

  static ApplicationEventModule toDto(ApplicationEventEntity entity) {
    return new ApplicationEventModule(
        entity,
        entity.getEventType(),
        entity.getMessage(),
        entity.getEventStatus(),
        entity.getEventTimestamp(),
        entity.getId(),
        entity.getJobId(),
        ""
    ) {};
  }

  static Set<ApplicationEventEntity> toEntitySet(Set<ApplicationEventModule> dtoSet) {
    return dtoSet.stream()
        .map(ApplicationEventMapper::toEntity)
        .collect(Collectors.toSet());
  }

  static Set<ApplicationEventModule> toDtoSet(Set<ApplicationEventEntity> entitySet) {
    return entitySet.stream()
        .map(ApplicationEventMapper::toDto)
        .collect(Collectors.toSet());
  }

}
