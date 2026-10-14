package com.invoicemind.ai.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

  @Query("""
      select a from AuditLogEntity a
      where a.organizationId = :org
        and (:action is null or a.action = :action)
        and (:entityType is null or a.entityType = :entityType)
        and (:userId is null or a.userId = :userId)
        and a.createdAt >= :from and a.createdAt < :to
      order by a.createdAt desc, a.id desc""")
  Page<AuditLogEntity> search(@Param("org") UUID org, @Param("action") String action,
                              @Param("entityType") String entityType, @Param("userId") UUID userId,
                              @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

  List<AuditLogEntity> findByOrganizationIdAndEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc(
      UUID organizationId, String entityType, UUID entityId);
}
