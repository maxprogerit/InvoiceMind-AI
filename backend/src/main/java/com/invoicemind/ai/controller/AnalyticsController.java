package com.invoicemind.ai.controller;

import com.invoicemind.ai.analytics.AnalyticsService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
  private final AnalyticsService analyticsService;

  @GetMapping("/summary")
  public Map<String, Object> summary() { return analyticsService.analyticsSummary(); }

  @GetMapping("/spend-trend")
  public List<Map<String, Object>> spendTrend() { return analyticsService.spendTrend(); }

  @GetMapping("/document-volume")
  public List<Map<String, Object>> documentVolume() { return analyticsService.documentVolumeTrend(); }

  @GetMapping("/confidence-trend")
  public List<Map<String, Object>> confidenceTrend() { return analyticsService.confidenceTrend(); }

  @GetMapping("/vendor-spend")
  public List<Map<String, Object>> vendorSpend() { return analyticsService.vendorSpend(); }
}

