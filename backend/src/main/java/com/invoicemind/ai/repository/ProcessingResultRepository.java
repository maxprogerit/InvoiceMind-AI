package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.ProcessingResultEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingResultRepository extends JpaRepository<ProcessingResultEntity, Long> {
  Optional<ProcessingResultEntity> findTopByDocumentIdOrderByCreatedAtDesc(Long documentId);
  List<ProcessingResultEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
