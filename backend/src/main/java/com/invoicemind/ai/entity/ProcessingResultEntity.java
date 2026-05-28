package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "processing_results")
@Getter
@Setter
public class ProcessingResultEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private Long documentId;
  private String extractionJson;
  private BigDecimal confidenceScore;
  private BigDecimal anomalyScore;
  private String status;
  private Instant createdAt;
}
