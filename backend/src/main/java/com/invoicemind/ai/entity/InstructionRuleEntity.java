package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "instruction_rules")
@Getter
@Setter
public class InstructionRuleEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private String ruleType;
  private String name;
  private String pattern;
  private String actionConfig;
  private Boolean active;
  private Instant createdAt;
}
