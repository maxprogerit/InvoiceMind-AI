package com.invoicemind.ai.invoice;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<VendorEntity, UUID> {
  Optional<VendorEntity> findByOrganizationIdAndNormalizedName(UUID organizationId, String normalizedName);
}
