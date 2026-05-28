package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLogEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private Long userId;
  private String action;
  private String entityType;
  private Long entityId;
  private String metadataJson;
  private Instant createdAt;
}
