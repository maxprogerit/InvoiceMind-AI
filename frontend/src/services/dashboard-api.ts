import { apiGet } from "./api";

export interface DashboardKpis {
  documentsProcessed: string;
  automationRate: string;
  invoiceVolume: string;
  aiConfidence: string;
  pendingApprovals: string;
}

export interface DashboardOverview {
  kpis: DashboardKpis;
  recentInvoices: Record<string, unknown>[];
  topVendors: Record<string, unknown>[];
  workflowActivity: Record<string, unknown>[];
  aiInsights: Record<string, unknown>[];
}

export const dashboardApi = {
  overview: () => apiGet<DashboardOverview>("/dashboard/overview"),
};
