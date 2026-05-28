package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.AuditLogEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
  List<AuditLogEntity> findTop50ByOrderByCreatedAtDesc();
  List<AuditLogEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId, PageRequest page);
  List<AuditLogEntity> findByOrganizationIdAndActionOrderByCreatedAtDesc(Long organizationId, String action);
  List<AuditLogEntity> findByOrganizationIdAndCreatedAtAfterOrderByCreatedAtDesc(Long organizationId, Instant after);
  long countByOrganizationId(Long organizationId);
}
