import { apiGet } from "./api";

export interface AnalyticsSummary {
  ocrAccuracy: number;
  duplicateDetectionRate: number;
  costReductionPercent: number;
  workflowEfficiencyScore: number;
  forecastedMonthlySavings: number;
  totalDocuments: number;
  successRate: number;
  // Optional extended fields
  automationRate?: number;
  avgConfidence?: number;
  totalInvoiceAmount?: number;
}

export interface SpendPoint {
  month: string;
  spend: number;
  savings: number;
}

export interface VolumePoint {
  month: string;
  invoices: number;
  receipts: number;
}

export interface ConfidencePoint {
  month: string;
  confidence: number;
}

export interface VendorSpend {
  vendor: string;
  spend: number;
  invoices: number;
}

export const analyticsApi = {
  summary: () => apiGet<AnalyticsSummary>("/analytics/summary"),
  spendTrend: () => apiGet<SpendPoint[]>("/analytics/spend-trend"),
  documentVolume: () => apiGet<VolumePoint[]>("/analytics/document-volume"),
  confidenceTrend: () => apiGet<ConfidencePoint[]>("/analytics/confidence-trend"),
  vendorSpend: () => apiGet<VendorSpend[]>("/analytics/vendor-spend"),
};
