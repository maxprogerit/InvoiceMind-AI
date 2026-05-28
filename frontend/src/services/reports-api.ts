import { apiGet, apiPost, apiDownload } from "./api";

export interface Report {
  id: number;
  reportType: string;
  format: string;
  generatedBy: string;
  storagePath: string | null;
  createdAt: string;
  status: string;
  dateFrom: string | null;
  dateTo: string | null;
  recordCount: number;
}

export interface GenerateReportRequest {
  reportType: string;
  format?: string;
  dateFrom?: string;
  dateTo?: string;
}

export const reportsApi = {
  list: () => apiGet<Report[]>("/reports"),
  generate: (req: GenerateReportRequest) => apiPost<Report>("/reports/generate", req),
  download: (id: number) => apiDownload(`/reports/${id}/download`, `report-${id}.csv`),
};
