package com.invoicemind.ai.notification;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
  public List<Map<String, Object>> list() {
    return List.of(
      Map.of("id", 1, "type", "approval", "message", "Invoice INV-2026-4011 awaits your decision", "read", false),
      Map.of("id", 2, "type", "anomaly", "message", "Duplicate risk detected in 2 invoices", "read", false)
    );
  }
}
