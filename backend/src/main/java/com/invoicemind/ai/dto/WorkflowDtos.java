package com.invoicemind.ai.dto;

public class WorkflowDtos {
  public record WorkflowExecutionRequest(Long workflowId, Long documentId) {}
  public record WorkflowExecutionResponse(Long executionId, String status, String kafkaTraceId) {}
}
