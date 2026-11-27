import type { components } from "./schema";

type S = components["schemas"];

export type InvoiceStatus = S["InvoiceSummary"]["status"];
export type DocumentType = S["InvoiceSummary"]["documentType"];
export type InvoiceSummary = S["InvoiceSummary"];
export type InvoiceDetail = S["InvoiceDetail"];
export type LineItem = S["LineItemDto"];
export type Issue = S["IssueDto"];
export type Permissions = S["Permissions"];
export type UpdateInvoiceRequest = S["UpdateInvoiceRequest"];
export type AuthUser = S["UserDto"];
export type Role = AuthUser["role"];
export type TeamMember = S["TeamMember"];
export type AuditEntry = S["AuditEntryDto"];
export type NotificationItem = S["NotificationDto"];
export type Summary = S["Summary"];
export type SpendByVendor = S["SpendByVendor"];
export type SpendByMonth = S["SpendByMonth"];
export type ByStatus = S["ByStatus"];
export type VolumeByDay = S["VolumeByDay"];
export type Organization = S["OrganizationDto"];
export type FxStatus = S["FxStatus"];
export type EmailIngestStatus = S["EmailIngestStatus"];
export type WebhookEndpoint = S["EndpointDto"];
export type CreatedWebhook = S["CreatedEndpointDto"];
export type WebhookDelivery = S["DeliveryDto"];
export type ManualRate = S["ManualRate"];
export type Vendor = S["VendorDto"];
export type VendorMemory = S["MemoryDto"];
export type Correction = S["CorrectionDto"];
export type QueueCounts = S["QueueCounts"];
export type MemoryApplied = S["MemoryAppliedDto"];
/** Work queues of the review screen (filters on the invoice list). */
export type ReviewQueue = "NEEDS_REVIEW" | "POSSIBLE_DUPLICATE" | "UNVERIFIED_VENDOR" | "OVERDUE";
export type VendorStatus = Vendor["status"];
export type DuplicateInfo = S["DuplicateInfo"];
export type DuplicateDecision = S["DuplicateDecisionRequest"]["decision"];
/** Error envelope returned by every failing API call (see GlobalExceptionHandler in the backend). */
export interface ErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  requestId?: string;
  fieldErrors?: Record<string, string>;
}

export type Page<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };

export const ALL_STATUSES: InvoiceStatus[] = [
  "UPLOADED",
  "PROCESSING",
  "EXTRACTED",
  "NEEDS_REVIEW",
  "VALIDATED",
  "APPROVED",
  "REJECTED",
  "FAILED",
];

/** Statuses in which the pipeline is still working and the UI should expect change. */
export const IN_FLIGHT: ReadonlySet<InvoiceStatus> = new Set(["UPLOADED", "PROCESSING", "EXTRACTED"]);

export function isReviewer(role: Role | undefined): boolean {
  return role === "APPROVER" || role === "ADMIN";
}

/** Live event pushed over STOMP (/user/queue/invoices and the reviewers topic). */
export interface StatusEvent {
  invoiceId: string;
  status: InvoiceStatus;
  vendorName?: string;
  invoiceNumber?: string;
  failureReason?: string;
  at: string;
}

export type KnowledgeDocument = S["DocumentDto"];
export type KnowledgeAnswer = S["AnswerDto"];
export type KnowledgeSource = S["Source"];

// ------------------------------------------------------------------- firms
export type FirmRole = NonNullable<AuthUser["firmRole"]>;
export type OrganizationOption = S["OrganizationOption"];
export type FirmInfo = S["FirmInfo"];
export type ClientSummary = S["ClientSummary"];
export type ClientAccess = S["ClientAccess"];
export type FirmMember = S["FirmMemberDto"];
export type FirmTeam = S["FirmTeam"];
export type ClientRef = S["ClientRef"];
