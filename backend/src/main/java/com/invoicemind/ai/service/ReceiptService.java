package com.invoicemind.ai.service;

import com.invoicemind.ai.audit.AuditLogService;
import com.invoicemind.ai.entity.ReceiptEntity;
import com.invoicemind.ai.entity.VendorEntity;
import com.invoicemind.ai.repository.ReceiptRepository;
import com.invoicemind.ai.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ReceiptService {
  private final ReceiptRepository receiptRepository;
  private final VendorRepository vendorRepository;
  private final FileStorageService fileStorageService;
  private final AiParsingService aiParsingService;
  private final AuditLogService auditLogService;

  public List<ReceiptEntity> listAll() {
    return receiptRepository.findByOrganizationIdOrderByCreatedAtDesc(1L);
  }

  public ReceiptEntity getById(Long id) {
    return receiptRepository.findById(id).orElseThrow(() -> new RuntimeException("Receipt not found"));
  }

  public ReceiptEntity uploadAndProcess(MultipartFile file, Long userId) {
    fileStorageService.store(file);
    var ocr = aiParsingService.parseDocument(file.getOriginalFilename() != null ? file.getOriginalFilename() : "receipt.pdf");
    VendorEntity vendor = vendorRepository.findByOrganizationIdOrderByNameAsc(1L).stream().findFirst().orElse(null);

    ReceiptEntity receipt = new ReceiptEntity();
    receipt.setOrganizationId(1L);
    receipt.setMerchantName(ocr.vendorName());
    receipt.setTotalAmount(ocr.amount());
    receipt.setTaxAmount(ocr.amount().multiply(BigDecimal.valueOf(0.1)));
    receipt.setExpenseCategory(ocr.category());
    receipt.setConfidenceScore(ocr.confidenceScore());
    receipt.setReceiptDate(LocalDate.now());
    receipt.setMerchantCategory("Retail");
    receipt.setUploadedBy(userId);
    receipt.setCreatedAt(Instant.now());
    receipt.setStatus("processed");
    if (vendor != null) receipt.setVendorId(vendor.getId());
    ReceiptEntity saved = receiptRepository.save(receipt);
    auditLogService.log(userId, "RECEIPT_UPLOADED", "receipt", saved.getId(),
        "{\"merchant\":\"" + ocr.vendorName() + "\",\"amount\":" + ocr.amount() + "}");
    return saved;
  }

  public void delete(Long id, Long userId) {
    receiptRepository.findById(id).orElseThrow(() -> new RuntimeException("Receipt not found"));
    receiptRepository.deleteById(id);
    auditLogService.log(userId, "RECEIPT_DELETED", "receipt", id, "{}");
  }

  public byte[] exportCsv() {
    StringBuilder sb = new StringBuilder("ID,Merchant,Amount,Tax,Category,Confidence,Date,Status\n");
    receiptRepository.findByOrganizationIdOrderByCreatedAtDesc(1L).forEach(r ->
        sb.append(String.join(",", String.valueOf(r.getId()), "\"" + r.getMerchantName() + "\"",
            String.valueOf(r.getTotalAmount()), String.valueOf(r.getTaxAmount()),
            r.getExpenseCategory() != null ? r.getExpenseCategory() : "",
            String.valueOf(r.getConfidenceScore()),
            r.getReceiptDate() != null ? r.getReceiptDate().toString() : "",
            r.getStatus() != null ? r.getStatus() : "")).append("\n"));
    return sb.toString().getBytes();
  }
}
