export type InvoiceStatus = "processing" | "ready" | "approved" | "rejected" | "exported";

export interface InvoiceRecord {
  id: string;
  vendor: string;
  amount: number;
  currency: string;
  dueDate: string;
  status: InvoiceStatus;
  confidence: number;
  category: string;
}

export interface KpiMetric {
  label: string;
  value: string;
  change: string;
}

export interface WorkflowExecution {
  id: string;
  workflowName: string;
  status: "running" | "success" | "failed";
  durationMs: number;
  startedAt: string;
}
