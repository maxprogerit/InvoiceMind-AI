package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.VendorEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<VendorEntity, Long> {
  List<VendorEntity> findByOrganizationIdOrderByNameAsc(Long organizationId);
  Optional<VendorEntity> findByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);
  List<VendorEntity> findByOrganizationIdAndNameContainingIgnoreCaseOrderByNameAsc(Long organizationId, String name);
}
