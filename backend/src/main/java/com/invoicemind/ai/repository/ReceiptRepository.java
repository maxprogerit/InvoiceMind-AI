package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.ReceiptEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<ReceiptEntity, Long> {
  List<ReceiptEntity> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
  List<ReceiptEntity> findByOrganizationIdAndVendorIdOrderByCreatedAtDesc(Long organizationId, Long vendorId);
}
