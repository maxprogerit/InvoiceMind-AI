package com.invoicemind.ai.controller;

import com.invoicemind.ai.approval.ApprovalService;
import com.invoicemind.ai.entity.ApprovalEntity;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {
  private final ApprovalService approvalService;

  @GetMapping
  public List<ApprovalEntity> list() { return approvalService.listAll(); }

  @GetMapping("/pending")
  public List<ApprovalEntity> pending() { return approvalService.pending(); }

  @GetMapping("/{id}")
  public ApprovalEntity get(@PathVariable Long id) { return approvalService.getById(id); }

  @PostMapping("/{id}/decision")
  public ApprovalEntity decide(@PathVariable Long id, @RequestBody Map<String, String> payload) {
    return approvalService.decide(id, payload.getOrDefault("decision", "approved"),
        payload.getOrDefault("comment", ""), 1L);
  }

  @GetMapping("/count/pending")
  public Map<String, Long> countPending() { return Map.of("count", approvalService.countPending()); }
}
