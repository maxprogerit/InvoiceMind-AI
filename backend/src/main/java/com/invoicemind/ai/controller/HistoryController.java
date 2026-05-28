package com.invoicemind.ai.controller;

import com.invoicemind.ai.audit.AuditLogService;
import com.invoicemind.ai.entity.AuditLogEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
public class HistoryController {
  private final AuditLogService auditLogService;

  @GetMapping
  public List<AuditLogEntity> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return auditLogService.listPaged(PageRequest.of(page, size));
  }

  @GetMapping("/audit")
  public List<AuditLogEntity> recent() { return auditLogService.recent(); }
}

