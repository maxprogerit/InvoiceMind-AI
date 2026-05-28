package com.invoicemind.ai.controller;

import com.invoicemind.ai.repository.AiInsightRepository;
import com.invoicemind.ai.workflow.WorkflowExecutionService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MiscController {
  private final WorkflowExecutionService workflowExecutionService;
  private final AiInsightRepository aiInsightRepository;

  @GetMapping("/executions/live")
  public Object executions() { return workflowExecutionService.liveExecutions(); }

  @GetMapping("/ai/insights")
  public Object insights() { return aiInsightRepository.findTop10ByOrderByCreatedAtDesc(); }

  @GetMapping("/receipts")
  public Object receipts() {
    return Map.of("uploadedToday", 184, "categorizationRate", 96.4, "merchantRecognition", 98.1, "topCategories", List.of("Travel", "Meals", "Office"));
  }

  @GetMapping("/instructions")
  public Object rules() {
    return List.of(
      Map.of("name", "EU VAT Check", "active", true, "condition", "invoice.vendorRegion == 'EU'"),
      Map.of("name", "PO Required", "active", true, "condition", "invoice.amount > 1000"),
      Map.of("name", "Auto-approve", "active", true, "condition", "confidence > 0.97 && anomalyScore < 0.15")
    );
  }

  @GetMapping("/settings")
  public Object settings() {
    return Map.of(
      "organization", "InvoiceMind Global",
      "theme", "system",
      "ocrProvider", "SmartOCR Simulation",
      "aiModel", "invoice-parser-v4",
      "erpIntegrations", List.of("SAP S/4HANA", "NetSuite", "Microsoft Dynamics")
    );
  }
}
