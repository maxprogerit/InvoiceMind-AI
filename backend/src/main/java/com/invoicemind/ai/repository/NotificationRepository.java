package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.NotificationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
  List<NotificationEntity> findByUserIdAndReadFlagFalseOrderByCreatedAtDesc(Long userId);
  List<NotificationEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
