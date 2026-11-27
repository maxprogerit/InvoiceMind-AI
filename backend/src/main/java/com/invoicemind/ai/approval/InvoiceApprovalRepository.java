package com.invoicemind.ai.approval;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceApprovalRepository extends JpaRepository<InvoiceApprovalEntity, Long> {
  List<InvoiceApprovalEntity> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);

  @Modifying(flushAutomatically = true, clearAutomatically = false)
  @Query("delete from InvoiceApprovalEntity a where a.invoiceId = :invoice")
  int deleteByInvoiceId(@Param("invoice") UUID invoiceId);
}
