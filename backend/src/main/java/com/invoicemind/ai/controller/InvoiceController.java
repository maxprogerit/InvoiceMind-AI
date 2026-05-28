package com.invoicemind.ai.controller;

import com.invoicemind.ai.audit.AuditLogService;
import com.invoicemind.ai.dto.InvoiceDtos.InvoiceResponse;
import com.invoicemind.ai.dto.InvoiceDtos.OcrResultResponse;
import com.invoicemind.ai.service.AiParsingService;
import com.invoicemind.ai.service.DocumentService;
import com.invoicemind.ai.service.InvoiceService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {
  private final InvoiceService invoiceService;
  private final DocumentService documentService;
  private final AiParsingService aiParsingService;
  private final AuditLogService auditLogService;

  @GetMapping
  public List<InvoiceResponse> list() { return invoiceService.listInvoices(); }

  @GetMapping("/{id}")
  public InvoiceResponse details(@PathVariable Long id) { return invoiceService.getInvoice(id); }

  @PutMapping("/{id}/status")
  public Map<String, Object> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
    var invoice = invoiceService.updateStatus(id, body.getOrDefault("status", "processing"), 1L);
    return Map.of("id", invoice.getId(), "status", invoice.getStatus());
  }

  @GetMapping("/stats")
  public Map<String, Object> stats() { return invoiceService.getStats(); }

  @GetMapping("/export/csv")
  public ResponseEntity<byte[]> exportCsv() {
    byte[] csv = invoiceService.exportCsv();
    auditLogService.log(1L, "EXPORT_COMPLETED", "invoice", null, "{\"format\":\"csv\"}");
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoices.csv")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(csv);
  }

  @PostMapping("/upload")
  public Map<String, Object> uploadAndProcess(@RequestPart("file") MultipartFile file) {
    var doc = documentService.registerUpload(file, 1L);
    OcrResultResponse extraction = aiParsingService.parseDocument(
        file.getOriginalFilename() == null ? "invoice.pdf" : file.getOriginalFilename());
    var invoice = invoiceService.createFromOcr(doc.getId(), extraction.invoiceNumber(),
        extraction.amount(), extraction.confidenceScore(), extraction.category());
    aiParsingService.emitLiveProcessing(doc.getId(), "trace-" + doc.getId());
    auditLogService.log(1L, "UPLOAD_INVOICE", "invoice", invoice.getId(),
        "{\"file\":\"" + file.getOriginalFilename() + "\"}");
    return Map.of("documentId", doc.getId(), "invoiceId", invoice.getId(), "ocr", extraction);
  }
}
