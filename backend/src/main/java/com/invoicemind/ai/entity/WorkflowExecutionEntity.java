package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "workflow_executions")
@Getter
@Setter
public class WorkflowExecutionEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private Long workflowId;
  private Long documentId;
  private String status;
  private Instant startedAt;
  private Instant endedAt;
  private Long durationMs;
  private String kafkaTraceId;
  private Integer progressPercent;
  private String logsJson;
  private String errorMessage;
}
