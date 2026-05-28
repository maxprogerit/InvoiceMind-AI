package com.invoicemind.ai.workflow;

import com.invoicemind.ai.dto.WorkflowDtos.WorkflowExecutionResponse;
import com.invoicemind.ai.entity.WorkflowExecutionEntity;
import com.invoicemind.ai.service.AiParsingService;
import com.invoicemind.ai.repository.WorkflowExecutionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowExecutionService {
  private final WorkflowExecutionRepository workflowExecutionRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final AiParsingService aiParsingService;

  public WorkflowExecutionResponse startExecution(Long workflowId, Long documentId) {
    String traceId = UUID.randomUUID().toString();
    WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
    execution.setOrganizationId(1L);
    execution.setWorkflowId(workflowId);
    execution.setDocumentId(documentId);
    execution.setStatus("running");
    execution.setStartedAt(Instant.now());
    execution.setKafkaTraceId(traceId);
    workflowExecutionRepository.save(execution);
    kafkaTemplate.send("workflow-events", Map.of("event", "WORKFLOW_STARTED", "executionId", execution.getId(), "traceId", traceId));
    aiParsingService.emitLiveProcessing(documentId, traceId);
    return new WorkflowExecutionResponse(execution.getId(), execution.getStatus(), traceId);
  }

  public List<WorkflowExecutionEntity> liveExecutions() {
    return workflowExecutionRepository.findTop20ByOrderByStartedAtDesc();
  }
}
