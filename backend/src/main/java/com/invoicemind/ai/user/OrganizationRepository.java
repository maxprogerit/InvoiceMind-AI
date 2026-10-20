package com.invoicemind.ai.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {
  java.util.Optional<OrganizationEntity> findByIngestToken(String ingestToken);

  java.util.List<OrganizationEntity> findByFirmIdOrderByNameAsc(UUID firmId);

  java.util.Optional<OrganizationEntity> findByIdAndFirmId(UUID id, UUID firmId);
}
