package com.invoicemind.ai.invoice;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceCorrectionRepository extends JpaRepository<InvoiceCorrectionEntity, Long> {
  List<InvoiceCorrectionEntity> findByInvoiceIdOrderByCreatedAtAscIdAsc(UUID invoiceId);
}
