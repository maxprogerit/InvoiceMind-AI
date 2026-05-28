import { apiGet, apiPost } from "./api";

export interface Execution {
  id: number;
  status: string;
  progressPercent: number | null;
  durationMs: number | null;
  kafkaTraceId: string | null;
  documentId: number | null;
  workflowId: number | null;
  workflowName: string | null;
  startedAt: string;
  createdAt: string | null;
  endedAt: string | null;
  errorMessage: string | null;
  logsJson: string | null;
}

export interface ExecutionStats {
  total: number;
  completed: number;
  failed: number;
  running: number;
}

export const executionsApi = {
  list: () => apiGet<Execution[]>("/executions"),
  get: (id: number) => apiGet<Execution>(`/executions/${id}`),
  retry: (id: number) => apiPost<Execution>(`/executions/${id}/retry`, {}),
  cancel: (id: number) => apiPost<Execution>(`/executions/${id}/cancel`, {}),
  stats: () => apiGet<ExecutionStats>("/executions/stats"),
};
