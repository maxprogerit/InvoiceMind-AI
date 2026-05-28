package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "workflows")
@Getter
@Setter
public class WorkflowEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private String name;
  private String version;
  private String definitionJson;
  private Boolean published;
  private String status;
  private Instant createdAt;
}
