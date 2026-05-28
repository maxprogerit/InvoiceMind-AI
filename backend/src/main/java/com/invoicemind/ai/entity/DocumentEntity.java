package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "documents")
@Getter
@Setter
public class DocumentEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private Long uploadedBy;
  private String fileName;
  private String fileType;
  private String storagePath;
  private String processingStatus;
  private Instant createdAt;
  private java.math.BigDecimal confidenceScore;
  private String documentType;
}
