package com.invoicemind.ai.analytics;

import com.invoicemind.ai.analytics.AnalyticsDtos.ByStatus;
import com.invoicemind.ai.analytics.AnalyticsDtos.SpendByMonth;
import com.invoicemind.ai.analytics.AnalyticsDtos.SpendByVendor;
import com.invoicemind.ai.analytics.AnalyticsDtos.Summary;
import com.invoicemind.ai.analytics.AnalyticsDtos.VolumeByDay;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Analytics")
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
  private final AnalyticsService service;

  @GetMapping("/summary")
  public Summary summary() {
    return service.summary(CurrentUser.get());
  }

  @GetMapping("/spend-by-vendor")
  public SpendByVendor spendByVendor(@RequestParam(required = false) String currency,
                                     @RequestParam(defaultValue = "10") int limit) {
    return service.spendByVendor(CurrentUser.get(), currency, limit);
  }

  @GetMapping("/spend-by-month")
  public SpendByMonth spendByMonth(@RequestParam(required = false) String currency,
                                   @RequestParam(defaultValue = "12") int months) {
    return service.spendByMonth(CurrentUser.get(), currency, months);
  }

  @GetMapping("/by-status")
  public ByStatus byStatus() {
    return service.byStatus(CurrentUser.get());
  }

  @GetMapping("/volume")
  public VolumeByDay volume(@RequestParam(defaultValue = "30") int days) {
    return service.volumeByDay(CurrentUser.get(), days);
  }
}
