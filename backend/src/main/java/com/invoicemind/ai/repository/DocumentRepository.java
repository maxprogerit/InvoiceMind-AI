package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.DocumentEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
  List<DocumentEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
  List<DocumentEntity> findByOrganizationIdAndProcessingStatusOrderByCreatedAtDesc(Long organizationId, String status);
  @Query("SELECT COUNT(d) FROM DocumentEntity d WHERE d.organizationId = ?1")
  long countByOrganizationId(Long organizationId);
}
