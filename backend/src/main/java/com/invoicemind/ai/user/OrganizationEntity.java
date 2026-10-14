package com.invoicemind.ai.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "organizations")
public class OrganizationEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  private String name;
  private Instant createdAt = Instant.now();

  public OrganizationEntity(String name) { this.name = name; }
}
