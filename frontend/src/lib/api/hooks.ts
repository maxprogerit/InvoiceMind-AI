"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api, authFetch, fetchBlob, toApiError, unwrap } from "./client";
import {
  IN_FLIGHT,
  type AuditEntry,
  type FxStatus,
  type Organization,
  type DocumentType,
  type ByStatus,
  type InvoiceDetail,
  type InvoiceStatus,
  type InvoiceSummary,
  type KnowledgeAnswer,
  type KnowledgeDocument,
  type NotificationItem,
  type Page,
  type SpendByMonth,
  type SpendByVendor,
  type Summary,
  type TeamMember,
  type UpdateInvoiceRequest,
  type VolumeByDay,
} from "./types";
import { useLive } from "@/lib/ws/live-store";

export interface InvoiceFilters {
  status?: InvoiceStatus[];
  documentType?: DocumentType;
  q?: string;
  vendor?: string;
  currency?: string;
  from?: string;
  to?: string;
  minTotal?: number;
  maxTotal?: number;
  page?: number;
  size?: number;
  sort?: string;
}

const clean = <T extends object>(o: T) =>
  Object.fromEntries(Object.entries(o).filter(([, v]) => v !== undefined && v !== "" && !(Array.isArray(v) && v.length === 0))) as T;

// ---------------------------------------------------------------- invoices
export function useInvoices(filters: InvoiceFilters, options?: { enabled?: boolean }) {
  return useQuery({
    enabled: options?.enabled ?? true,
    queryKey: ["invoices", filters],
    queryFn: () =>
      unwrap(api.GET("/api/v1/invoices", { params: { query: clean(filters) } })) as Promise<Page<InvoiceSummary>>,
    placeholderData: keepPreviousData,
  });
}

export function useInvoice(id: string | undefined) {
  const connected = useLive((s) => s.connected);
  return useQuery({
    enabled: !!id,
    queryKey: ["invoice", id],
    queryFn: () => unwrap(api.GET("/api/v1/invoices/{id}", { params: { path: { id: id! } } })) as Promise<InvoiceDetail>,
    // Live events normally keep this fresh; poll only while the pipeline is working and the socket is down.
    refetchInterval: (q) => (!connected && q.state.data && IN_FLIGHT.has(q.state.data.status) ? 3000 : false),
  });
}

export function useInvoiceAudit(id: string | undefined) {
  return useQuery({
    enabled: !!id,
    queryKey: ["invoice", id, "audit"],
    queryFn: () => unwrap(api.GET("/api/v1/invoices/{id}/audit", { params: { path: { id: id! } } })) as Promise<AuditEntry[]>,
  });
}

export function useOcrText(id: string | undefined, enabled: boolean) {
  return useQuery({
    enabled: !!id && enabled,
    queryKey: ["invoice", id, "ocr"],
    queryFn: () => unwrap(api.GET("/api/v1/invoices/{id}/ocr-text", { params: { path: { id: id! } } })),
  });
}

/** The original upload as an object URL (the endpoint needs the bearer token, so <img src> cannot call it). */
export function useDocumentBlob(id: string | undefined) {
  return useQuery({
    enabled: !!id,
    queryKey: ["invoice", id, "file"],
    queryFn: async () => {
      const { blob } = await fetchBlob(`/api/v1/invoices/${id}/file`);
      return { url: URL.createObjectURL(blob), type: blob.type };
    },
    staleTime: Infinity,
    gcTime: 60_000,
  });
}

function useInvalidateInvoices() {
  const qc = useQueryClient();
  return (id?: string) => {
    void qc.invalidateQueries({ queryKey: ["invoices"] });
    void qc.invalidateQueries({ queryKey: ["analytics"] });
    if (id) {
      void qc.invalidateQueries({ queryKey: ["invoice", id] });
    }
  };
}

export function useUpdateInvoice(id: string) {
  const invalidate = useInvalidateInvoices();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateInvoiceRequest) =>
      unwrap(api.PUT("/api/v1/invoices/{id}", { params: { path: { id } }, body })) as Promise<InvoiceDetail>,
    onSuccess: (data) => {
      qc.setQueryData(["invoice", id], data);
      invalidate(id);
    },
  });
}

export function useDecision(id: string, kind: "approve" | "reject") {
  const invalidate = useInvalidateInvoices();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (comment?: string) =>
      unwrap(
        kind === "approve"
          ? api.POST("/api/v1/invoices/{id}/approve", { params: { path: { id } }, body: { comment } })
          : api.POST("/api/v1/invoices/{id}/reject", { params: { path: { id } }, body: { comment } }),
      ) as Promise<InvoiceDetail>,
    onSuccess: (data) => {
      qc.setQueryData(["invoice", id], data);
      invalidate(id);
      void qc.invalidateQueries({ queryKey: ["invoice", id, "audit"] });
    },
  });
}

export function useRetry(id: string) {
  const invalidate = useInvalidateInvoices();
  return useMutation({
    mutationFn: () => unwrap(api.POST("/api/v1/invoices/{id}/retry", { params: { path: { id } } })),
    onSuccess: () => invalidate(id),
  });
}

export function useDeleteInvoice(id: string) {
  const invalidate = useInvalidateInvoices();
  return useMutation({
    mutationFn: async () => {
      await unwrap(api.DELETE("/api/v1/invoices/{id}", { params: { path: { id } } }));
    },
    onSuccess: () => invalidate(),
  });
}

// --------------------------------------------------------------- analytics
export const useSummary = () =>
  useQuery({ queryKey: ["analytics", "summary"], queryFn: () => unwrap(api.GET("/api/v1/analytics/summary")) as Promise<Summary> });

/** `converted` reports every currency in the organisation base currency (the `currency` argument is then ignored). */
export const useSpendByVendor = (currency: string | undefined, converted = false) =>
  useQuery({
    enabled: converted || !!currency,
    queryKey: ["analytics", "vendor", converted ? "converted" : currency],
    queryFn: () =>
      unwrap(
        api.GET("/api/v1/analytics/spend-by-vendor", { params: { query: { currency: converted ? undefined : currency, limit: 8, converted } } }),
      ) as Promise<SpendByVendor>,
  });

export const useSpendByMonth = (currency: string | undefined, converted = false, months = 12) =>
  useQuery({
    enabled: converted || !!currency,
    queryKey: ["analytics", "month", converted ? "converted" : currency, months],
    queryFn: () =>
      unwrap(
        api.GET("/api/v1/analytics/spend-by-month", { params: { query: { currency: converted ? undefined : currency, months, converted } } }),
      ) as Promise<SpendByMonth>,
  });

export const useByStatus = () =>
  useQuery({ queryKey: ["analytics", "status"], queryFn: () => unwrap(api.GET("/api/v1/analytics/by-status")) as Promise<ByStatus> });

export const useVolume = (days = 30) =>
  useQuery({
    queryKey: ["analytics", "volume", days],
    queryFn: () => unwrap(api.GET("/api/v1/analytics/volume", { params: { query: { days } } })) as Promise<VolumeByDay>,
  });

// ----------------------------------------------------------- notifications
export function useNotifications() {
  return useQuery({
    queryKey: ["notifications"],
    queryFn: () => unwrap(api.GET("/api/v1/notifications")) as Promise<{ items: NotificationItem[]; unreadCount: number }>,
  });
}

export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id?: string) => {
      const res = id
        ? await api.POST("/api/v1/notifications/{id}/read", { params: { path: { id } } })
        : await api.POST("/api/v1/notifications/read-all");
      if (!res.response.ok) throw new Error("Could not update notifications");
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["notifications"] }),
  });
}

// -------------------------------------------------------------------- team
export function useUsers() {
  return useQuery({ queryKey: ["users"], queryFn: () => unwrap(api.GET("/api/v1/users")) as Promise<TeamMember[]> });
}

export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { email: string; fullName: string; role: TeamMember["role"]; password: string }) =>
      unwrap(api.POST("/api/v1/users", { body })),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["users"] }),
  });
}

export function useUpdateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }: { id: string; role?: TeamMember["role"]; enabled?: boolean }) =>
      unwrap(api.PATCH("/api/v1/users/{id}", { params: { path: { id } }, body })),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["users"] }),
  });
}

// ------------------------------------------------------------------- audit
export interface AuditFilters {
  action?: string;
  entityType?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export function useAudit(filters: AuditFilters) {
  return useQuery({
    queryKey: ["audit", filters],
    queryFn: () => unwrap(api.GET("/api/v1/audit", { params: { query: clean(filters) } })) as Promise<Page<AuditEntry>>,
    placeholderData: keepPreviousData,
  });
}

// --------------------------------------------------------------- knowledge
export function useKnowledgeDocuments(enabled = true) {
  return useQuery({
    enabled,
    queryKey: ["knowledge"],
    queryFn: () => unwrap(api.GET("/api/v1/knowledge/documents")) as Promise<KnowledgeDocument[]>,
  });
}

export function useAddKnowledgeText() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { title: string; content: string }) =>
      unwrap(api.POST("/api/v1/knowledge/documents", { body })) as Promise<KnowledgeDocument>,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["knowledge"] }),
  });
}

export function useUploadKnowledge() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ file, title }: { file: File; title?: string }) => {
      const form = new FormData();
      form.append("file", file);
      if (title) form.append("title", title);
      const res = await authFetch("/api/v1/knowledge/documents/upload", { method: "POST", body: form });
      if (!res.ok) throw await toApiError(res);
      return (await res.json()) as KnowledgeDocument;
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["knowledge"] }),
  });
}

export function useDeleteKnowledge() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await unwrap(api.DELETE("/api/v1/knowledge/documents/{id}", { params: { path: { id } } }));
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["knowledge"] }),
  });
}

export function useAsk() {
  return useMutation({
    mutationFn: (question: string) => unwrap(api.POST("/api/v1/knowledge/ask", { body: { question } })) as Promise<KnowledgeAnswer>,
  });
}

export function useExplainInvoice(id: string) {
  return useMutation({
    mutationFn: (question?: string) =>
      unwrap(api.POST("/api/v1/invoices/{id}/explain", { params: { path: { id } }, body: question ? { question } : undefined })) as Promise<KnowledgeAnswer>,
  });
}

// ---------------------------------------------------- organisation / currency
export const useOrganization = () =>
  useQuery({ queryKey: ["organization"], queryFn: () => unwrap(api.GET("/api/v1/organization")) as Promise<Organization> });

export const useFxStatus = (enabled = true) =>
  useQuery({ enabled, queryKey: ["fx"], queryFn: () => unwrap(api.GET("/api/v1/fx")) as Promise<FxStatus> });

function useInvalidateMoney() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: ["fx"] });
    void qc.invalidateQueries({ queryKey: ["organization"] });
    void qc.invalidateQueries({ queryKey: ["analytics"] });
  };
}

export function useSetBaseCurrency() {
  const invalidate = useInvalidateMoney();
  return useMutation({
    mutationFn: (baseCurrency: string) => unwrap(api.PUT("/api/v1/organization", { body: { baseCurrency } })) as Promise<Organization>,
    onSuccess: invalidate,
  });
}

export function useSetManualRate() {
  const invalidate = useInvalidateMoney();
  return useMutation({
    mutationFn: (body: { currency: string; date: string; perEur: number }) => unwrap(api.PUT("/api/v1/fx/manual", { body })),
    onSuccess: invalidate,
  });
}

export function useDeleteManualRate() {
  const invalidate = useInvalidateMoney();
  return useMutation({
    mutationFn: async (id: number) => {
      await unwrap(api.DELETE("/api/v1/fx/manual/{id}", { params: { path: { id } } }));
    },
    onSuccess: invalidate,
  });
}
