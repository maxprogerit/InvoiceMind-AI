package com.invoicemind.ai.export;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
  public Map<String, Object> exportReport(String format, String reportType) {
    return Map.of(
      "exportId", "EXP-" + UUID.randomUUID().toString().substring(0, 8),
      "status", "queued",
      "format", format,
      "reportType", reportType,
      "estimatedReadyAt", Instant.now().plusSeconds(10).toString()
    );
  }
}
