"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api, authFetch, fetchBlob, toApiError, unwrap } from "./client";
import {
  IN_FLIGHT,
  type AuditEntry,
  type AuthUser,
  type ClientAccess,
  type ClientSummary,
  type FirmInfo,
  type FirmRole,
  type FirmTeam,
  type OrganizationOption,
  type Role,
  type CreatedWebhook,
  type EmailIngestStatus,
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
  type WebhookDelivery,
  type WebhookEndpoint,
} from "./types";
import { useAuth } from "@/lib/auth/store";
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

// ---------------------------------------------------------------- webhooks
export const useWebhooks = (enabled = true) =>
  useQuery({ enabled, queryKey: ["webhooks"], queryFn: () => unwrap(api.GET("/api/v1/webhooks")) as Promise<WebhookEndpoint[]> });

export const useWebhookEvents = () =>
  useQuery({ queryKey: ["webhooks", "events"], queryFn: () => unwrap(api.GET("/api/v1/webhooks/events")) as Promise<string[]>, staleTime: Infinity });

export function useCreateWebhook() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { url: string; description?: string; events: string[] }) =>
      unwrap(api.POST("/api/v1/webhooks", { body })) as Promise<CreatedWebhook>,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["webhooks"] }),
  });
}

export function useUpdateWebhook() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...body }: { id: string; enabled?: boolean; events?: string[]; url?: string; description?: string }) =>
      unwrap(api.PATCH("/api/v1/webhooks/{id}", { params: { path: { id } }, body })),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["webhooks"] }),
  });
}

export function useDeleteWebhook() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      await unwrap(api.DELETE("/api/v1/webhooks/{id}", { params: { path: { id } } }));
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["webhooks"] }),
  });
}

export const useWebhookDeliveries = (id: string, enabled: boolean) =>
  useQuery({
    enabled,
    queryKey: ["webhooks", id, "deliveries"],
    queryFn: () => unwrap(api.GET("/api/v1/webhooks/{id}/deliveries", { params: { path: { id } } })) as Promise<WebhookDelivery[]>,
    refetchInterval: (q) => (q.state.data?.some((d) => d.status === "PENDING") ? 2000 : false),
  });

export function useTestWebhook(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => unwrap(api.POST("/api/v1/webhooks/{id}/test", { params: { path: { id } } })),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["webhooks", id, "deliveries"] }),
  });
}

export function useRetryDelivery(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (deliveryId: string) =>
      unwrap(api.POST("/api/v1/webhooks/{id}/deliveries/{deliveryId}/retry", { params: { path: { id, deliveryId } } })),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["webhooks", id, "deliveries"] }),
  });
}

// ------------------------------------------------------------- e-mail ingestion
export const useEmailIngest = (enabled = true) =>
  useQuery({
    enabled,
    queryKey: ["ingest", "email"],
    queryFn: () => unwrap(api.GET("/api/v1/ingest/email")) as Promise<EmailIngestStatus>,
    refetchInterval: 15_000, // new mail shows up without a reload
  });

export function useRotateEmailAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => unwrap(api.POST("/api/v1/ingest/email/token")) as Promise<EmailIngestStatus>,
    onSuccess: (data) => qc.setQueryData(["ingest", "email"], data),
  });
}

// ------------------------------------------------------------ public auth config
/** What the login page may offer (single sign-on). Public: no session needed. */
export const useAuthConfig = () =>
  useQuery({
    queryKey: ["auth", "config"],
    queryFn: () => unwrap(api.GET("/api/v1/auth/config")) as Promise<{ sso: { enabled: boolean; label?: string } }>,
    staleTime: 5 * 60_000,
  });

// ------------------------------------------------------------------- firms
/** The client companies the signed-in person can switch to. */
export function useOrganizations() {
  return useQuery({
    queryKey: ["organizations"],
    queryFn: () => unwrap(api.GET("/api/v1/auth/organizations")) as Promise<OrganizationOption[]>,
    staleTime: 60_000,
  });
}

/**
 * Moves the session into another client company. The server answers with a token for it; everything cached belongs to the
 * previous company, so the cache is reset, and the live connection re-opens by itself because it follows the company id.
 */
export function useSwitchOrganization() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (organizationId: string) =>
      unwrap(api.POST("/api/v1/auth/switch-organization", { body: { organizationId } })) as Promise<{ accessToken: string; user: AuthUser }>,
    onSuccess: async (res) => {
      useAuth.getState().setSession(res.accessToken, res.user);
      useLive.getState().reset();
      await qc.resetQueries();
    },
  });
}

export function useFirm(options?: { enabled?: boolean }) {
  return useQuery({
    enabled: options?.enabled ?? true,
    queryKey: ["firm"],
    queryFn: () => unwrap(api.GET("/api/v1/firm")) as Promise<FirmInfo>,
  });
}

export function useFirmClients() {
  return useQuery({
    queryKey: ["firm", "clients"],
    queryFn: () => unwrap(api.GET("/api/v1/firm/clients")) as Promise<ClientSummary[]>,
  });
}

function useFirmInvalidation() {
  const qc = useQueryClient();
  return () => Promise.all([qc.invalidateQueries({ queryKey: ["firm"] }), qc.invalidateQueries({ queryKey: ["organizations"] })]);
}

export function useCreateClient() {
  const invalidate = useFirmInvalidation();
  return useMutation({
    mutationFn: (body: { name: string; baseCurrency: string }) => unwrap(api.POST("/api/v1/firm/clients", { body })),
    onSuccess: invalidate,
  });
}

export function useSetClientActive() {
  const invalidate = useFirmInvalidation();
  return useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      unwrap(
        active
          ? api.POST("/api/v1/firm/clients/{id}/activate", { params: { path: { id } } })
          : api.POST("/api/v1/firm/clients/{id}/deactivate", { params: { path: { id } } }),
      ),
    onSuccess: invalidate,
  });
}

export function useFirmTeam() {
  return useQuery({ queryKey: ["firm", "team"], queryFn: () => unwrap(api.GET("/api/v1/firm/members")) as Promise<FirmTeam> });
}

export function useAddFirmMember() {
  const invalidate = useFirmInvalidation();
  return useMutation({
    mutationFn: (body: {
      email: string;
      fullName: string;
      password: string;
      firmRole: FirmRole;
      access: ClientAccess[];
    }) => unwrap(api.POST("/api/v1/firm/members", { body })),
    onSuccess: invalidate,
  });
}

/** Gives a member a role in a client, or (role = null) takes the client away. */
export function useSetAccess() {
  const invalidate = useFirmInvalidation();
  return useMutation({
    mutationFn: ({ userId, organizationId, role }: { userId: string; organizationId: string; role: Role | null }) =>
      role
        ? unwrap(
            api.PUT("/api/v1/firm/members/{userId}/access/{organizationId}", {
              params: { path: { userId, organizationId } },
              body: { role },
            }),
          )
        : unwrap(api.DELETE("/api/v1/firm/members/{userId}/access/{organizationId}", { params: { path: { userId, organizationId } } })),
    onSuccess: invalidate,
  });
}

export function useRemoveFirmMember() {
  const invalidate = useFirmInvalidation();
  return useMutation({
    mutationFn: (userId: string) => unwrap(api.DELETE("/api/v1/firm/members/{userId}", { params: { path: { userId } } })),
    onSuccess: invalidate,
  });
}
