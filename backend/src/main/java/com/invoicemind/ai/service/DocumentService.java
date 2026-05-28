package com.invoicemind.ai.service;

import com.invoicemind.ai.approval.ApprovalService;
import com.invoicemind.ai.audit.AuditLogService;
import com.invoicemind.ai.dto.InvoiceDtos.OcrResultResponse;
import com.invoicemind.ai.entity.DocumentEntity;
import com.invoicemind.ai.entity.InvoiceEntity;
import com.invoicemind.ai.entity.ProcessingResultEntity;
import com.invoicemind.ai.entity.VendorEntity;
import com.invoicemind.ai.entity.WorkflowExecutionEntity;
import com.invoicemind.ai.repository.DocumentRepository;
import com.invoicemind.ai.repository.ProcessingResultRepository;
import com.invoicemind.ai.repository.VendorRepository;
import com.invoicemind.ai.repository.WorkflowExecutionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentService {
  private final DocumentRepository documentRepository;
  private final FileStorageService fileStorageService;
  private final ProcessingResultRepository processingResultRepository;
  private final VendorRepository vendorRepository;
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final AuditLogService auditLogService;
  private final AiParsingService aiParsingService;
  private final SimpMessagingTemplate messagingTemplate;
  private final InvoiceService invoiceService;
  private final ApprovalService approvalService;

  public DocumentEntity registerUpload(MultipartFile file, Long userId) {
    DocumentEntity entity = new DocumentEntity();
    entity.setOrganizationId(1L);
    entity.setUploadedBy(userId);
    entity.setFileName(file.getOriginalFilename());
    entity.setFileType(file.getContentType());
    entity.setStoragePath(fileStorageService.store(file));
    entity.setProcessingStatus("UPLOADED");
    entity.setCreatedAt(Instant.now());
    entity.setDocumentType(detectDocumentType(file.getOriginalFilename()));
    DocumentEntity saved = documentRepository.save(entity);
    auditLogService.log(userId, "DOCUMENT_UPLOADED", "document", saved.getId(),
        "{\"fileName\":\"" + file.getOriginalFilename() + "\"}");
    return saved;
  }

  @Async
  public void processDocument(Long documentId, Long userId, String traceId) {
    broadcast(documentId, "OCR_STARTED", 10, traceId);
    auditLogService.log(null, "OCR_STARTED", "document", documentId,
        "{\"traceId\":\"" + traceId + "\"}");
    sleep(600);

    broadcast(documentId, "OCR_PROGRESS", 40, traceId);
    sleep(500);
    broadcast(documentId, "OCR_PROGRESS", 70, traceId);
    sleep(400);

    DocumentEntity doc = documentRepository.findById(documentId).orElseThrow();
    OcrResultResponse ocr = aiParsingService.parseDocument(doc.getFileName() != null ? doc.getFileName() : "document.pdf");

    ProcessingResultEntity result = new ProcessingResultEntity();
    result.setOrganizationId(1L);
    result.setDocumentId(documentId);
    result.setConfidenceScore(ocr.confidenceScore());
    result.setAnomalyScore(BigDecimal.valueOf(0.02));
    result.setStatus("completed");
    result.setCreatedAt(Instant.now());
    result.setExtractionJson("{\"invoiceNumber\":\"" + ocr.invoiceNumber() + "\",\"amount\":" + ocr.amount()
        + ",\"vendor\":\"" + ocr.vendorName() + "\",\"category\":\"" + ocr.category() + "\"}");
    processingResultRepository.save(result);

    doc.setProcessingStatus("COMPLETED");
    doc.setConfidenceScore(ocr.confidenceScore());
    documentRepository.save(doc);

    VendorEntity vendor = matchOrCreateVendor(ocr.vendorName());
    InvoiceEntity invoice = invoiceService.createFromOcr(documentId, ocr.invoiceNumber(), ocr.amount(),
        ocr.confidenceScore(), ocr.category());
    invoice.setVendorId(vendor.getId());

    broadcast(documentId, "OCR_COMPLETED", 100, traceId);
    auditLogService.log(null, "OCR_COMPLETED", "document", documentId,
        "{\"confidence\":" + ocr.confidenceScore() + "}");

    createExecution(documentId, traceId);

    boolean needsApproval = ocr.confidenceScore().doubleValue() < 0.92 || ocr.amount().doubleValue() > 10000;
    if (needsApproval) {
      approvalService.createForInvoice(invoice.getId(), userId);
      broadcast(documentId, "APPROVAL_CREATED", 100, traceId);
    }

    broadcastDashboardUpdate();
  }

  public List<DocumentEntity> listAll() {
    return documentRepository.findByOrganizationIdOrderByCreatedAtDesc(1L);
  }

  public DocumentEntity getById(Long id) {
    return documentRepository.findById(id).orElseThrow(() -> new RuntimeException("Document not found"));
  }

  public void deleteById(Long id, Long userId) {
    documentRepository.findById(id).orElseThrow(() -> new RuntimeException("Document not found"));
    documentRepository.deleteById(id);
    auditLogService.log(userId, "DOCUMENT_DELETED", "document", id, "{}");
  }

  public DocumentEntity retryProcessing(Long id, Long userId) {
    DocumentEntity doc = documentRepository.findById(id).orElseThrow();
    doc.setProcessingStatus("UPLOADED");
    documentRepository.save(doc);
    String traceId = "retry-" + UUID.randomUUID().toString().substring(0, 8);
    processDocument(id, userId, traceId);
    return doc;
  }

  private VendorEntity matchOrCreateVendor(String vendorName) {
    return vendorRepository.findByOrganizationIdAndNameIgnoreCase(1L, vendorName)
        .orElseGet(() -> {
          var v = vendorRepository.findByOrganizationIdAndNameContainingIgnoreCaseOrderByNameAsc(1L,
              vendorName.split(" ")[0]).stream().findFirst().orElse(null);
          if (v != null) return v;
          VendorEntity newV = new VendorEntity();
          newV.setOrganizationId(1L);
          newV.setName(vendorName);
          newV.setPaymentTerms("Net 30");
          newV.setRiskScore(BigDecimal.valueOf(0.1));
          return vendorRepository.save(newV);
        });
  }

  private void createExecution(Long documentId, String traceId) {
    WorkflowExecutionEntity exec = new WorkflowExecutionEntity();
    exec.setOrganizationId(1L);
    exec.setDocumentId(documentId);
    exec.setStatus("completed");
    exec.setStartedAt(Instant.now().minusMillis(1500));
    exec.setEndedAt(Instant.now());
    exec.setDurationMs(1500L);
    exec.setKafkaTraceId(traceId);
    exec.setProgressPercent(100);
    workflowExecutionRepository.save(exec);
  }

  private void broadcast(Long documentId, String status, int progress, String traceId) {
    messagingTemplate.convertAndSend("/topic/executions",
        Map.of("documentId", documentId, "status", status, "progress", progress, "traceId", traceId));
  }

  private void broadcastDashboardUpdate() {
    messagingTemplate.convertAndSend("/topic/dashboard", Map.of("event", "METRICS_UPDATED"));
  }

  private String detectDocumentType(String fileName) {
    if (fileName == null) return "document";
    String lower = fileName.toLowerCase();
    if (lower.contains("receipt")) return "receipt";
    if (lower.contains("invoice") || lower.contains("inv")) return "invoice";
    return "document";
  }

  private void sleep(long ms) {
    try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
  }
}
