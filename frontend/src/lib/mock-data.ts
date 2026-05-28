import { InvoiceRecord, KpiMetric, WorkflowExecution } from "@/types";

export const kpis: KpiMetric[] = [
  { label: "Documents processed", value: "128,492", change: "+18.4%" },
  { label: "Automation rate", value: "93.7%", change: "+4.9%" },
  { label: "Invoice volume", value: "$12.8M", change: "+11.2%" },
  { label: "AI confidence", value: "97.1%", change: "+1.3%" }
];

export const invoices: InvoiceRecord[] = [
  { id: "INV-2026-4012", vendor: "Apex Logistics", amount: 12842.51, currency: "USD", dueDate: "2026-06-04", status: "approved", confidence: 98.2, category: "Operations" },
  { id: "INV-2026-4011", vendor: "BytePeak Hosting", amount: 6423.95, currency: "USD", dueDate: "2026-06-01", status: "processing", confidence: 91.3, category: "Infrastructure" },
  { id: "INV-2026-4010", vendor: "Nova Office Supply", amount: 2140.88, currency: "USD", dueDate: "2026-06-03", status: "ready", confidence: 95.8, category: "Office" },
  { id: "INV-2026-4009", vendor: "Orion Consulting", amount: 22200, currency: "USD", dueDate: "2026-05-31", status: "rejected", confidence: 84.9, category: "Professional Services" },
  { id: "INV-2026-4008", vendor: "Helios Energy", amount: 4890.25, currency: "USD", dueDate: "2026-06-06", status: "exported", confidence: 99.1, category: "Utilities" }
];

export const spendTrend = [
  { month: "Jan", spend: 820000, savings: 120000 },
  { month: "Feb", spend: 910000, savings: 148000 },
  { month: "Mar", spend: 1080000, savings: 164000 },
  { month: "Apr", spend: 1260000, savings: 201000 },
  { month: "May", spend: 1390000, savings: 229000 },
  { month: "Jun", spend: 1530000, savings: 248000 }
];

export const topVendors = [
  { name: "Apex Logistics", invoices: 842, amount: 1.84 },
  { name: "BytePeak Hosting", invoices: 490, amount: 1.11 },
  { name: "Helios Energy", invoices: 302, amount: 0.92 },
  { name: "Orion Consulting", invoices: 276, amount: 0.73 }
];

export const liveExecutions: WorkflowExecution[] = [
  { id: "EXEC-7701", workflowName: "Invoice AP Auto-Approval", status: "running", durationMs: 1820, startedAt: "2s ago" },
  { id: "EXEC-7700", workflowName: "Duplicate Detection", status: "success", durationMs: 931, startedAt: "11s ago" },
  { id: "EXEC-7699", workflowName: "Vendor Risk Scoring", status: "failed", durationMs: 2401, startedAt: "15s ago" }
];
