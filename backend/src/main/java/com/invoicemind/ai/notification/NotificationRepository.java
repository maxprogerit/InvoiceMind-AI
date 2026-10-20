package com.invoicemind.ai.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
  List<NotificationEntity> findTop50ByUserIdAndOrganizationIdOrderByCreatedAtDesc(UUID userId, UUID organizationId);

  long countByUserIdAndOrganizationIdAndReadAtIsNull(UUID userId, UUID organizationId);

  Optional<NotificationEntity> findByIdAndUserIdAndOrganizationId(UUID id, UUID userId, UUID organizationId);

  @Modifying
  @Query("update NotificationEntity n set n.readAt = :now where n.userId = :userId and n.organizationId = :org and n.readAt is null")
  int markAllRead(@Param("userId") UUID userId, @Param("org") UUID organizationId, @Param("now") Instant now);
}
