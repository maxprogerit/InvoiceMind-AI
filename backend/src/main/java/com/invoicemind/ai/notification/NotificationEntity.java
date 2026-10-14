package com.invoicemind.ai.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "notifications")
public class NotificationEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  private UUID organizationId;
  private UUID userId;
  private String type;
  private String title;
  private String body;
  private UUID invoiceId;
  private Instant readAt;
  private Instant createdAt = Instant.now();
}
