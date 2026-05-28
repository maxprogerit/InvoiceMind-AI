import { apiGet, apiPost } from "./api";

export interface Approval {
  id: number;
  invoiceId: number;
  status: string;
  aiRecommendation: string | null;
  comment: string | null;
  decidedAt: string | null;
  requestedBy: number;
  approverId: number | null;
}

export const approvalsApi = {
  list: () => apiGet<Approval[]>("/approvals"),
  pending: () => apiGet<Approval[]>("/approvals/pending"),
  get: (id: number) => apiGet<Approval>(`/approvals/${id}`),
  decide: (id: number, decision: "approved" | "rejected", comment?: string) =>
    apiPost<Approval>(`/approvals/${id}/decision`, { decision, comment: comment ?? "" }),
  countPending: () => apiGet<{ count: number }>("/approvals/count/pending"),
};
