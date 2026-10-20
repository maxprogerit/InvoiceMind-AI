package com.invoicemind.ai.firm;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FirmRepository extends JpaRepository<FirmEntity, UUID> {}
