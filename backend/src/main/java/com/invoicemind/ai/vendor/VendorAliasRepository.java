package com.invoicemind.ai.vendor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorAliasRepository extends JpaRepository<VendorAliasEntity, Long> {
  List<VendorAliasEntity> findByOrganizationIdOrderByAliasAsc(UUID organizationId);

  List<VendorAliasEntity> findByVendorIdOrderByAliasAsc(UUID vendorId);

  Optional<VendorAliasEntity> findByOrganizationIdAndNormalizedAlias(UUID organizationId, String normalizedAlias);

  Optional<VendorAliasEntity> findByIdAndVendorId(Long id, UUID vendorId);
}
