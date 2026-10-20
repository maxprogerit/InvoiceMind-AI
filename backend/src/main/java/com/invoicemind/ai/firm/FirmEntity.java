package com.invoicemind.ai.firm;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "firms")
public class FirmEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  private String name;
  /** Label only; there is no billing behind it. */
  private String plan = "FREE";
  /** Documents the whole firm (all client companies together) may upload per calendar month; null means unlimited. */
  private Integer monthlyDocumentLimit;
  private Instant createdAt = Instant.now();

  public FirmEntity(String name) { this.name = name; }
}
