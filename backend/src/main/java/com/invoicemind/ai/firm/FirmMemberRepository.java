package com.invoicemind.ai.firm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirmMemberRepository extends JpaRepository<FirmMemberEntity, FirmMemberEntity.Key> {
  List<FirmMemberEntity> findByUserId(UUID userId);

  List<FirmMemberEntity> findByFirmId(UUID firmId);

  Optional<FirmMemberEntity> findByFirmIdAndUserId(UUID firmId, UUID userId);
}
