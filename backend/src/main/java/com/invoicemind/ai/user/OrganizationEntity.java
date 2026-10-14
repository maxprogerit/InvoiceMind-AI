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
  /** ISO 4217 code analytics are reported in (amounts are converted at the rate of each invoice's date). */
  private String baseCurrency = "EUR";
  /** Routes e-mail to this organisation: invoices+{token}@domain. Secret-ish: anyone with it can submit documents for review. */
  private String ingestToken = newIngestToken();
  private Instant createdAt = Instant.now();

  public OrganizationEntity(String name) { this.name = name; }

  public static String newIngestToken() {
    byte[] bytes = new byte[12];
    new java.security.SecureRandom().nextBytes(bytes);
    return java.util.HexFormat.of().formatHex(bytes);
  }
}
