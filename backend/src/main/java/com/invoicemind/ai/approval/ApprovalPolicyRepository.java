package com.invoicemind.ai.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalPolicyRepository extends JpaRepository<ApprovalPolicyEntity, ApprovalPolicyEntity.Key> {
  List<ApprovalPolicyEntity> findByOrganizationIdOrderByCurrencyAsc(UUID organizationId);

  Optional<ApprovalPolicyEntity> findByOrganizationIdAndCurrency(UUID organizationId, String currency);

  void deleteByOrganizationId(UUID organizationId);
}
