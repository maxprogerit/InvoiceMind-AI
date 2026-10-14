package com.invoicemind.ai.invoice;

import jakarta.persistence.*;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "vendors")
public class VendorEntity {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  private UUID organizationId;
  private String name;
  private String normalizedName;
  private Instant createdAt = Instant.now();

  public VendorEntity(UUID organizationId, String name) {
    this.organizationId = organizationId;
    this.name = name.trim();
    this.normalizedName = normalize(name);
  }

  /** Case-, accent- and punctuation-insensitive identity: "ACME GmbH." == "acme gmbh". */
  public static String normalize(String name) {
    String n = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .trim();
    return n.length() > 300 ? n.substring(0, 300) : n;
  }
}
