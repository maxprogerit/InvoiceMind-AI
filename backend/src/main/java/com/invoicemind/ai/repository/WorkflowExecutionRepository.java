package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.WorkflowExecutionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, Long> {
  List<WorkflowExecutionEntity> findTop20ByOrderByStartedAtDesc();
  List<WorkflowExecutionEntity> findByOrganizationIdOrderByStartedAtDesc(Long organizationId);
  List<WorkflowExecutionEntity> findByOrganizationIdAndStatusOrderByStartedAtDesc(Long organizationId, String status);
  long countByOrganizationIdAndStatus(Long organizationId, String status);
  long countByOrganizationId(Long organizationId);
}
