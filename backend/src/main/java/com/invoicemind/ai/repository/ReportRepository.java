package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.ReportEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<ReportEntity, Long> {
  List<ReportEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
