package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "approvals")
@Getter
@Setter
public class ApprovalEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private Long invoiceId;
  private Long requestedBy;
  private Long approverId;
  private String status;
  private String comment;
  private Instant decidedAt;
  private String aiRecommendation;
}
