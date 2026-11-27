package com.invoicemind.ai.vendor;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Another name the same vendor appears under ("ACME Ltd" for "ACME Limited"); matched like the vendor's own name. */
@Getter @Setter @NoArgsConstructor
@Entity @Table(name = "vendor_aliases")
public class VendorAliasEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private UUID vendorId;
  private UUID organizationId;
  private String alias;
  private String normalizedAlias;
  private UUID createdBy;
  private Instant createdAt = Instant.now();

  public VendorAliasEntity(UUID vendorId, UUID organizationId, String alias, UUID createdBy) {
    this.vendorId = vendorId;
    this.organizationId = organizationId;
    this.alias = alias.trim();
    this.normalizedAlias = VendorEntity.normalize(alias);
    this.createdBy = createdBy;
  }
}
