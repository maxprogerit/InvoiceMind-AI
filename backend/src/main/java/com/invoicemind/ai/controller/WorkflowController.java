package com.invoicemind.ai.controller;

import com.invoicemind.ai.dto.WorkflowDtos.WorkflowExecutionRequest;
import com.invoicemind.ai.dto.WorkflowDtos.WorkflowExecutionResponse;
import com.invoicemind.ai.entity.WorkflowEntity;
import com.invoicemind.ai.entity.WorkflowExecutionEntity;
import com.invoicemind.ai.repository.WorkflowRepository;
import com.invoicemind.ai.workflow.WorkflowExecutionService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {
  private final WorkflowExecutionService workflowExecutionService;
  private final WorkflowRepository workflowRepository;

  @GetMapping
  public List<WorkflowEntity> list() { return workflowRepository.findByOrganizationIdOrderByCreatedAtDesc(1L); }

  @GetMapping("/{id}")
  public WorkflowEntity get(@PathVariable Long id) { return workflowRepository.findById(id).orElseThrow(); }

  @PostMapping
  public WorkflowEntity create(@RequestBody WorkflowEntity workflow) {
    workflow.setOrganizationId(1L);
    workflow.setCreatedAt(Instant.now());
    if (workflow.getStatus() == null) workflow.setStatus("draft");
    return workflowRepository.save(workflow);
  }

  @PutMapping("/{id}")
  public WorkflowEntity update(@PathVariable Long id, @RequestBody WorkflowEntity update) {
    WorkflowEntity existing = workflowRepository.findById(id).orElseThrow();
    if (update.getName() != null) existing.setName(update.getName());
    if (update.getStatus() != null) existing.setStatus(update.getStatus());
    if (update.getDefinitionJson() != null) existing.setDefinitionJson(update.getDefinitionJson());
    return workflowRepository.save(existing);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    workflowRepository.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/execute")
  public WorkflowExecutionResponse execute(@RequestBody WorkflowExecutionRequest request) {
    return workflowExecutionService.startExecution(request.workflowId(), request.documentId());
  }

  @GetMapping("/executions/live")
  public List<WorkflowExecutionEntity> liveExecutions() { return workflowExecutionService.liveExecutions(); }
}

