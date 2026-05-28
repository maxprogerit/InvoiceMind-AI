package com.invoicemind.ai.controller;

import com.invoicemind.ai.entity.DocumentEntity;
import com.invoicemind.ai.service.DocumentService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {
  private final DocumentService documentService;

  @GetMapping
  public List<DocumentEntity> list() { return documentService.listAll(); }

  @GetMapping("/{id}")
  public DocumentEntity get(@PathVariable Long id) { return documentService.getById(id); }

  @PostMapping("/upload")
  public Map<String, Object> upload(@RequestPart("file") MultipartFile file) {
    var doc = documentService.registerUpload(file, 1L);
    String traceId = UUID.randomUUID().toString().substring(0, 12);
    documentService.processDocument(doc.getId(), 1L, traceId);
    return Map.of("documentId", doc.getId(), "status", doc.getProcessingStatus(),
        "storagePath", doc.getStoragePath(), "traceId", traceId);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    documentService.deleteById(id, 1L);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/retry")
  public DocumentEntity retry(@PathVariable Long id) {
    return documentService.retryProcessing(id, 1L);
  }
}
