package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.WorkflowEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, Long> {
  List<WorkflowEntity> findByOrganizationIdOrderByIdDesc(Long organizationId);
  List<WorkflowEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
  List<WorkflowEntity> findByOrganizationIdAndPublishedTrueOrderByIdDesc(Long organizationId);
}
