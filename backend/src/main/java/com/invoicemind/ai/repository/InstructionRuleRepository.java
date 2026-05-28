package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.InstructionRuleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstructionRuleRepository extends JpaRepository<InstructionRuleEntity, Long> {
  List<InstructionRuleEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
  List<InstructionRuleEntity> findByOrganizationIdAndActiveTrueOrderByCreatedAtDesc(Long organizationId);
}
