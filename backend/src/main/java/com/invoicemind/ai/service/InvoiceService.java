package com.invoicemind.ai.service;

import com.invoicemind.ai.audit.AuditLogService;
import com.invoicemind.ai.dto.InvoiceDtos.InvoiceResponse;
import com.invoicemind.ai.entity.InvoiceEntity;
import com.invoicemind.ai.entity.VendorEntity;
import com.invoicemind.ai.mapper.InvoiceMapper;
import com.invoicemind.ai.repository.InvoiceRepository;
import com.invoicemind.ai.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvoiceService {
  private final InvoiceRepository invoiceRepository;
  private final VendorRepository vendorRepository;
  private final AuditLogService auditLogService;

  public List<InvoiceResponse> listInvoices() {
    return invoiceRepository.findByOrganizationIdOrderByIdDesc(1L).stream().map(this::toResponse).toList();
  }

  public InvoiceResponse getInvoice(Long id) {
    return invoiceRepository.findById(id).map(this::toResponse).orElseThrow();
  }

  public InvoiceEntity updateStatus(Long id, String status, Long userId) {
    InvoiceEntity invoice = invoiceRepository.findById(id).orElseThrow();
    String oldStatus = invoice.getStatus();
    invoice.setStatus(status);
    if ("paid".equals(status)) invoice.setPaymentStatus("paid");
    InvoiceEntity saved = invoiceRepository.save(invoice);
    auditLogService.log(userId, "INVOICE_STATUS_CHANGED", "invoice", id,
        "{\"from\":\"" + oldStatus + "\",\"to\":\"" + status + "\"}");
    return saved;
  }

  public InvoiceEntity createFromOcr(Long documentId, String invoiceNumber, BigDecimal amount,
      BigDecimal confidence, String category) {
    VendorEntity vendor = vendorRepository.findByOrganizationIdOrderByNameAsc(1L).stream().findFirst()
        .orElseGet(() -> {
          VendorEntity v = new VendorEntity();
          v.setOrganizationId(1L); v.setName("Unknown Vendor"); v.setPaymentTerms("Net 30");
          v.setRiskScore(BigDecimal.valueOf(0.1));
          return vendorRepository.save(v);
        });
    InvoiceEntity invoice = new InvoiceEntity();
    invoice.setOrganizationId(1L); invoice.setDocumentId(documentId); invoice.setVendorId(vendor.getId());
    invoice.setInvoiceNumber(invoiceNumber); invoice.setTotalAmount(amount); invoice.setCurrency("USD");
    invoice.setInvoiceDate(LocalDate.now().minusDays(2)); invoice.setDueDate(LocalDate.now().plusDays(28));
    invoice.setStatus("processing"); invoice.setConfidenceScore(confidence);
    invoice.setDuplicateFlag(Boolean.FALSE); invoice.setSmartCategory(category);
    invoice.setPaymentStatus("unpaid");
    InvoiceEntity saved = invoiceRepository.save(invoice);
    auditLogService.log(null, "INVOICE_CREATED", "invoice", saved.getId(),
        "{\"invoiceNumber\":\"" + invoiceNumber + "\",\"amount\":" + amount + "}");
    return saved;
  }

  public byte[] exportCsv() {
    StringBuilder sb = new StringBuilder("ID,Invoice Number,Vendor,Amount,Currency,Status,Confidence,Category\n");
    invoiceRepository.findByOrganizationIdOrderByIdDesc(1L).forEach(inv -> {
      String vendor = vendorRepository.findById(inv.getVendorId() != null ? inv.getVendorId() : 0L)
          .map(VendorEntity::getName).orElse("Unknown");
      sb.append(String.join(",", String.valueOf(inv.getId()), inv.getInvoiceNumber(),
          "\"" + vendor + "\"", String.valueOf(inv.getTotalAmount()), inv.getCurrency(),
          inv.getStatus(), String.valueOf(inv.getConfidenceScore()), inv.getSmartCategory() != null ? inv.getSmartCategory() : "")).append("\n");
    });
    return sb.toString().getBytes();
  }

  public Map<String, Object> getStats() {
    long total = invoiceRepository.countByOrganizationIdAndStatus(1L, "approved") +
        invoiceRepository.countByOrganizationIdAndStatus(1L, "processing") +
        invoiceRepository.countByOrganizationIdAndStatus(1L, "ready");
    BigDecimal totalAmount = invoiceRepository.sumTotalAmountByOrganizationId(1L);
    return Map.of("total", total, "totalAmount", totalAmount,
        "pending", invoiceRepository.countByOrganizationIdAndStatus(1L, "processing"),
        "approved", invoiceRepository.countByOrganizationIdAndStatus(1L, "approved"));
  }

  private InvoiceResponse toResponse(InvoiceEntity invoice) {
    String vendorName = invoice.getVendorId() != null
        ? vendorRepository.findById(invoice.getVendorId()).map(VendorEntity::getName).orElse("Unknown Vendor")
        : "Unknown Vendor";
    return InvoiceMapper.toResponse(invoice, vendorName);
  }
}
