package com.invoicemind.ai.controller;

import com.invoicemind.ai.entity.WorkflowExecutionEntity;
import com.invoicemind.ai.repository.WorkflowExecutionRepository;
import com.invoicemind.ai.repository.WorkflowRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
public class ExecutionController {
  private final WorkflowExecutionRepository executionRepository;
  private final WorkflowRepository workflowRepository;

  private Map<String, Object> enrich(WorkflowExecutionEntity exec) {
    Map<String, Object> m = new HashMap<>();
    m.put("id", exec.getId());
    m.put("status", exec.getStatus());
    m.put("progressPercent", exec.getProgressPercent());
    m.put("durationMs", exec.getDurationMs());
    m.put("kafkaTraceId", exec.getKafkaTraceId());
    m.put("documentId", exec.getDocumentId());
    m.put("workflowId", exec.getWorkflowId());
    m.put("startedAt", exec.getStartedAt());
    m.put("createdAt", exec.getStartedAt());
    m.put("endedAt", exec.getEndedAt());
    m.put("errorMessage", exec.getErrorMessage());
    m.put("logsJson", exec.getLogsJson());
    String wfName = exec.getWorkflowId() != null
        ? workflowRepository.findById(exec.getWorkflowId()).map(w -> w.getName()).orElse("Workflow #" + exec.getWorkflowId())
        : "Default Workflow";
    m.put("workflowName", wfName);
    return m;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    return executionRepository.findByOrganizationIdOrderByStartedAtDesc(1L)
        .stream().map(this::enrich).collect(Collectors.toList());
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable Long id) {
    return enrich(executionRepository.findById(id).orElseThrow());
  }

  @PostMapping("/{id}/retry")
  public Map<String, Object> retry(@PathVariable Long id) {
    WorkflowExecutionEntity exec = executionRepository.findById(id).orElseThrow();
    exec.setStatus("queued");
    exec.setProgressPercent(0);
    exec.setErrorMessage(null);
    return enrich(executionRepository.save(exec));
  }

  @PostMapping("/{id}/cancel")
  public Map<String, Object> cancel(@PathVariable Long id) {
    WorkflowExecutionEntity exec = executionRepository.findById(id).orElseThrow();
    exec.setStatus("cancelled");
    return enrich(executionRepository.save(exec));
  }

  @GetMapping("/stats")
  public Map<String, Long> stats() {
    return Map.of(
        "total", executionRepository.countByOrganizationId(1L),
        "completed", executionRepository.countByOrganizationIdAndStatus(1L, "completed"),
        "failed", executionRepository.countByOrganizationIdAndStatus(1L, "failed"),
        "running", executionRepository.countByOrganizationIdAndStatus(1L, "running"));
  }
}
