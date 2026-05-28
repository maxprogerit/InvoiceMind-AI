package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.ApprovalEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<ApprovalEntity, Long> {
  List<ApprovalEntity> findByStatusOrderByIdDesc(String status);
  List<ApprovalEntity> findByOrganizationIdOrderByIdDesc(Long organizationId);
  long countByOrganizationIdAndStatus(Long organizationId, String status);
}
