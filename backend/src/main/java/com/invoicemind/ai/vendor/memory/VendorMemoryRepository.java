package com.invoicemind.ai.vendor.memory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorMemoryRepository extends JpaRepository<VendorMemoryEntity, Long> {
  List<VendorMemoryEntity> findByVendorIdOrderByFieldAscLearnedAtDesc(UUID vendorId);

  List<VendorMemoryEntity> findByOrganizationId(UUID organizationId);

  @Query("""
      select m from VendorMemoryEntity m where m.vendorId = :vendor and m.field = :field
        and ((:extracted is null and m.whenExtracted is null) or m.whenExtracted = :extracted)""")
  Optional<VendorMemoryEntity> find(@Param("vendor") UUID vendorId, @Param("field") MemoryField field,
                                    @Param("extracted") String whenExtracted);

  Optional<VendorMemoryEntity> findByIdAndVendorId(Long id, UUID vendorId);
}
