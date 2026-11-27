package com.invoicemind.ai.vendor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorRepository extends JpaRepository<VendorEntity, UUID> {
  Optional<VendorEntity> findByOrganizationIdAndTaxId(UUID organizationId, String taxId);

  Optional<VendorEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

  /** Vendors whose own name, or one of whose aliases, normalises to {@code normalized}; oldest first. */
  @Query("""
      select v from VendorEntity v
      where v.organizationId = :org and (v.normalizedName = :normalized or v.id in (
        select a.vendorId from VendorAliasEntity a where a.organizationId = :org and a.normalizedAlias = :normalized))
      order by v.createdAt asc""")
  List<VendorEntity> findByNameOrAlias(@Param("org") UUID organizationId, @Param("normalized") String normalized);

  List<VendorEntity> findByOrganizationIdOrderByNameAsc(UUID organizationId);
}
