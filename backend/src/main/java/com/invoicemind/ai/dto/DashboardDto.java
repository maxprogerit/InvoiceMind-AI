package com.invoicemind.ai.dto;

import java.util.List;
import java.util.Map;

public record DashboardDto(
  Map<String, String> kpis,
  List<Map<String, Object>> recentInvoices,
  List<Map<String, Object>> topVendors,
  List<Map<String, Object>> workflowActivity,
  List<Map<String, Object>> aiInsights
) {}
