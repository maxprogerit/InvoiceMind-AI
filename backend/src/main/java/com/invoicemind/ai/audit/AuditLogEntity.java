package com.invoicemind.ai.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "audit_logs")
public class AuditLogEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private UUID organizationId;
  private UUID userId;
  private String action;
  private String entityType;
  private UUID entityId;
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> details;
  private Instant createdAt = Instant.now();
}
