package com.invoicemind.ai.controller;

import com.invoicemind.ai.entity.ReportEntity;
import com.invoicemind.ai.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportsController {
  private final ReportService reportService;

  @GetMapping
  public List<ReportEntity> list() { return reportService.listAll(); }

  @PostMapping("/generate")
  public ReportEntity generate(@RequestBody Map<String, String> body) {
    String type = body.getOrDefault("reportType", "monthly-invoices");
    String format = body.getOrDefault("format", "csv");
    LocalDate from = body.containsKey("dateFrom") ? LocalDate.parse(body.get("dateFrom")) : LocalDate.now().minusMonths(1);
    LocalDate to = body.containsKey("dateTo") ? LocalDate.parse(body.get("dateTo")) : LocalDate.now();
    return reportService.generate(type, format, from, to, 1L);
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<byte[]> download(@PathVariable Long id) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-" + id + ".csv")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(reportService.downloadCsv(id));
  }
}

