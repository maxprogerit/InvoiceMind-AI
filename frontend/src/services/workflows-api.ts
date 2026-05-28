import { apiGet, apiPost, apiPut, apiDelete } from "./api";

export interface Workflow {
  id: number;
  name: string;
  status: string | null;
  published: boolean;
  version: string;
  definition: string | null;
  definitionJson: string | null;
  createdAt: string | null;
}

export interface WorkflowExecution {
  id: number;
  status: string;
  progressPercent: number | null;
  durationMs: number | null;
  kafkaTraceId: string | null;
  startedAt: string;
}

export const workflowsApi = {
  list: () => apiGet<Workflow[]>("/workflows"),
  get: (id: number) => apiGet<Workflow>(`/workflows/${id}`),
  create: (data: Partial<Workflow>) => apiPost<Workflow>("/workflows", data),
  update: (id: number, data: Partial<Workflow>) => apiPut<Workflow>(`/workflows/${id}`, data),
  delete: (id: number) => apiDelete(`/workflows/${id}`),
  execute: (workflowId: number, documentId?: number) =>
    apiPost<{ executionId: number; status: string; traceId: string }>("/workflows/execute", {
      workflowId,
      documentId: documentId ?? null,
    }),
  liveExecutions: () => apiGet<WorkflowExecution[]>("/workflows/executions/live"),
};
