package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_insights")
@Getter
@Setter
public class AiInsightEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private String insightType;
  private String title;
  private String detail;
  private BigDecimal confidenceScore;
  private Instant createdAt;
}
