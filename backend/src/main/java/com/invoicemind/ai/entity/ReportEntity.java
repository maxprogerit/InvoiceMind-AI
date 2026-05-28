package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reports")
@Getter
@Setter
public class ReportEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private String reportType;
  private String format;
  private String generatedBy;
  private String storagePath;
  private Instant createdAt;
  private String status;
  private java.time.LocalDate dateFrom;
  private java.time.LocalDate dateTo;
  private Integer recordCount;
}
