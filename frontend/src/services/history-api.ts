import { apiGet } from "./api";

export interface AuditLog {
  id: number;
  userId: number | null;
  action: string;
  entityType: string;
  entityId: number | null;
  description?: string;
  metadataJson: string | null;
  createdAt: string;
}

export const historyApi = {
  list: (page = 0, size = 50) =>
    apiGet<AuditLog[]>(`/history?page=${page}&size=${size}`),
  recent: () => apiGet<AuditLog[]>("/history/audit"),
};
