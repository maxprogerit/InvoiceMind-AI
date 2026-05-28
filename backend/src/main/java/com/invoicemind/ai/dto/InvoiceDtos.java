package com.invoicemind.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class InvoiceDtos {
  public record InvoiceResponse(Long id, String invoiceNumber, String vendorName, BigDecimal totalAmount, String currency, LocalDate dueDate, String status, BigDecimal confidenceScore, String smartCategory) {}
  public record OcrResultResponse(String invoiceNumber, String vendorName, BigDecimal amount, BigDecimal confidenceScore, String category, String recommendation) {}
}
