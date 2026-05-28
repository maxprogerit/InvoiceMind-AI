package com.invoicemind.ai.mapper;

import com.invoicemind.ai.dto.InvoiceDtos.InvoiceResponse;
import com.invoicemind.ai.entity.InvoiceEntity;

public class InvoiceMapper {
  private InvoiceMapper() {}

  public static InvoiceResponse toResponse(InvoiceEntity invoice, String vendorName) {
    return new InvoiceResponse(
      invoice.getId(),
      invoice.getInvoiceNumber(),
      vendorName,
      invoice.getTotalAmount(),
      invoice.getCurrency(),
      invoice.getDueDate(),
      invoice.getStatus(),
      invoice.getConfidenceScore(),
      invoice.getSmartCategory()
    );
  }
}
