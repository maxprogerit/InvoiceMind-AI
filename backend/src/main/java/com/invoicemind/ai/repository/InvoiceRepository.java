package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.InvoiceEntity;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, Long> {
  List<InvoiceEntity> findByOrganizationIdOrderByIdDesc(Long organizationId);
  List<InvoiceEntity> findByOrganizationIdAndStatusOrderByIdDesc(Long organizationId, String status);
  List<InvoiceEntity> findByOrganizationIdAndVendorIdOrderByIdDesc(Long organizationId, Long vendorId);
  @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM InvoiceEntity i WHERE i.organizationId = ?1")
  BigDecimal sumTotalAmountByOrganizationId(Long organizationId);
  @Query("SELECT COUNT(i) FROM InvoiceEntity i WHERE i.organizationId = ?1 AND i.status = ?2")
  long countByOrganizationIdAndStatus(Long organizationId, String status);
}
