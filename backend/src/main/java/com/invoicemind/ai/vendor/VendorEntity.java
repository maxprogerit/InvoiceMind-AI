package com.invoicemind.ai.vendor;

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
  /** Normalised tax / VAT id ({@link #normalizeTaxId}); unique per organisation when present. */
  private String taxId;
  private String defaultCurrency;
  @Enumerated(EnumType.STRING)
  private VendorStatus status = VendorStatus.UNVERIFIED;
  private UUID verifiedBy;
  private Instant verifiedAt;
  private Instant createdAt = Instant.now();

  public VendorEntity(UUID organizationId, String name) {
    this.organizationId = organizationId;
    rename(name);
  }

  public void rename(String name) {
    this.name = name.trim();
    this.normalizedName = normalize(name);
  }

  public boolean isVerified() { return status == VendorStatus.VERIFIED; }

  /** Case-, accent- and punctuation-insensitive identity: "ACME GmbH." == "acme gmbh". */
  public static String normalize(String name) {
    String n = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .trim();
    return n.length() > 300 ? n.substring(0, 300) : n;
  }

  /**
   * "VAT: DE 123 456 789", "de123456789" and "USt-IdNr. DE123456789" are the same id: labels, spaces and punctuation
   * are dropped and letters upper-cased. Returns null when fewer than five characters remain (not a usable id).
   */
  public static String normalizeTaxId(String raw) {
    if (raw == null) return null;
    String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).toUpperCase(Locale.ROOT).trim();
    s = s.replaceFirst("^(VAT\\s*(REG(ISTRATION)?\\.?)?\\s*(NO|NUMBER|ID)?\\.?|UST-?\\s*ID(NR)?\\.?|USTIDNR\\.?|TAX\\s*ID|"
        + "TIN|EIN|ABN|GST(IN)?|ИНН|NIP|PIB)\\s*[:#.]?\\s*", "");
    s = s.replaceAll("[^\\p{L}\\p{N}]+", "");
    if (s.length() < 5) return null;
    return s.length() > 50 ? s.substring(0, 50) : s;
  }
}
