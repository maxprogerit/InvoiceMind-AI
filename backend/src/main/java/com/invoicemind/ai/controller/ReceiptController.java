package com.invoicemind.ai.controller;

import com.invoicemind.ai.entity.ReceiptEntity;
import com.invoicemind.ai.service.ReceiptService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {
  private final ReceiptService receiptService;

  @GetMapping
  public List<ReceiptEntity> list() { return receiptService.listAll(); }

  @GetMapping("/{id}")
  public ReceiptEntity get(@PathVariable Long id) { return receiptService.getById(id); }

  @PostMapping("/upload")
  public ReceiptEntity upload(@RequestPart("file") MultipartFile file) {
    return receiptService.uploadAndProcess(file, 1L);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    receiptService.delete(id, 1L);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/export/csv")
  public ResponseEntity<byte[]> exportCsv() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=receipts.csv")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(receiptService.exportCsv());
  }
}
