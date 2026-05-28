package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class NotificationEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private Long userId;
  private String title;
  private String body;
  private Boolean readFlag;
  private Instant createdAt;
}
