package com.invoicemind.ai.controller;

import com.invoicemind.ai.analytics.AnalyticsService;
import com.invoicemind.ai.dto.DashboardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
  private final AnalyticsService analyticsService;
  @GetMapping("/overview")
  public DashboardDto overview() { return analyticsService.dashboard(); }
}
