import type { InvoiceStatus } from "@/lib/api/types";

export type Tone = "neutral" | "accent" | "ok" | "warn" | "bad" | "info" | "violet";

export const STATUS_META: Record<InvoiceStatus, { label: string; tone: Tone; hint: string }> = {
  UPLOADED: { label: "Uploaded", tone: "neutral", hint: "Queued for processing" },
  PROCESSING: { label: "Processing", tone: "info", hint: "OCR and AI extraction are running" },
  EXTRACTED: { label: "Extracted", tone: "violet", hint: "Data extracted, validating" },
  NEEDS_REVIEW: { label: "Needs review", tone: "warn", hint: "A human check is required" },
  VALIDATED: { label: "Validated", tone: "ok", hint: "Ready for approval" },
  APPROVED: { label: "Approved", tone: "ok", hint: "Approved for payment" },
  REJECTED: { label: "Rejected", tone: "bad", hint: "Rejected by an approver" },
  FAILED: { label: "Failed", tone: "bad", hint: "Processing failed" },
};

/** The happy path rendered as a stepper on the upload and detail pages. */
export const PIPELINE_STEPS: { key: string; label: string; reached: (s: InvoiceStatus) => boolean }[] = [
  { key: "upload", label: "Uploaded", reached: () => true },
  { key: "ocr", label: "OCR + AI", reached: (s) => s !== "UPLOADED" },
  { key: "validate", label: "Validated", reached: (s) => ["NEEDS_REVIEW", "VALIDATED", "APPROVED", "REJECTED"].includes(s) },
  { key: "decide", label: "Decision", reached: (s) => s === "APPROVED" || s === "REJECTED" },
];

export const ISSUE_FIELD_LABEL: Record<string, string> = {
  vendorName: "Vendor",
  invoiceNumber: "Invoice number",
  invoiceDate: "Invoice date",
  dueDate: "Due date",
  currency: "Currency",
  subtotal: "Subtotal",
  tax: "Tax",
  total: "Total",
};
