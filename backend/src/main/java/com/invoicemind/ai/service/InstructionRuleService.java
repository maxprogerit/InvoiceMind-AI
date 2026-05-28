package com.invoicemind.ai.service;

import com.invoicemind.ai.audit.AuditLogService;
import com.invoicemind.ai.entity.InstructionRuleEntity;
import com.invoicemind.ai.repository.InstructionRuleRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstructionRuleService {
  private final InstructionRuleRepository ruleRepository;
  private final AuditLogService auditLogService;

  public List<InstructionRuleEntity> listAll() {
    return ruleRepository.findByOrganizationIdOrderByCreatedAtDesc(1L);
  }

  public InstructionRuleEntity create(InstructionRuleEntity rule) {
    rule.setOrganizationId(1L);
    rule.setCreatedAt(Instant.now());
    if (rule.getActive() == null) rule.setActive(true);
    InstructionRuleEntity saved = ruleRepository.save(rule);
    auditLogService.log(1L, "INSTRUCTION_RULE_CREATED", "instruction_rule", saved.getId(),
        "{\"name\":\"" + saved.getName() + "\",\"type\":\"" + saved.getRuleType() + "\"}");
    return saved;
  }

  public InstructionRuleEntity update(Long id, InstructionRuleEntity updated) {
    InstructionRuleEntity existing = ruleRepository.findById(id).orElseThrow();
    if (updated.getName() != null) existing.setName(updated.getName());
    if (updated.getRuleType() != null) existing.setRuleType(updated.getRuleType());
    if (updated.getPattern() != null) existing.setPattern(updated.getPattern());
    if (updated.getActionConfig() != null) existing.setActionConfig(updated.getActionConfig());
    if (updated.getActive() != null) existing.setActive(updated.getActive());
    return ruleRepository.save(existing);
  }

  public void delete(Long id) {
    ruleRepository.deleteById(id);
    auditLogService.log(1L, "INSTRUCTION_RULE_DELETED", "instruction_rule", id, "{}");
  }
}
