"use client";

import { Skeleton } from "@/components/ui/misc";
import { Card, CardLabel } from "@/components/ui/card";
import { useInvoiceAudit } from "@/lib/api/hooks";
import { formatDateTime } from "@/lib/format";

export const ACTION_LABEL: Record<string, string> = {
  INVOICE_UPLOADED: "Uploaded",
  PROCESSING_STARTED: "Processing started",
  EXTRACTION_COMPLETED: "Data extracted by AI",
  VALIDATION_COMPLETED: "Validation finished",
  PROCESSING_FAILED: "Processing failed",
  PROCESSING_RETRIED: "Processing retried",
  INVOICE_CORRECTED: "Fields corrected",
  INVOICE_APPROVED: "Approved",
  INVOICE_REJECTED: "Rejected",
  INVOICE_DELETED: "Deleted",
  EXPORT_CREATED: "Export created",
  LOGIN: "Signed in",
  ORGANIZATION_REGISTERED: "Organisation registered",
  USER_CREATED: "User created",
  USER_UPDATED: "User updated",
};

export function DetailLines({ details }: { details?: Record<string, unknown> }) {
  if (!details) return null;
  const entries = Object.entries(details).filter(([k]) => k !== "sha256");
  if (entries.length === 0) return null;
  return (
    <dl className="mt-1.5 space-y-0.5 font-mono text-[11px] text-dim">
      {entries.map(([k, v]) => (
        <div key={k} className="flex gap-2">
          <dt className="shrink-0 text-dim/70">{k}</dt>
          <dd className="min-w-0 break-words text-muted">{typeof v === "object" ? JSON.stringify(v) : String(v)}</dd>
        </div>
      ))}
    </dl>
  );
}

export function AuditTimeline({ invoiceId }: { invoiceId: string }) {
  const { data, isLoading } = useInvoiceAudit(invoiceId);
  return (
    <Card className="p-6">
      <CardLabel>History</CardLabel>
      {isLoading && <Skeleton className="mt-4 h-24" />}
      {data && (
        <ol className="mt-5 space-y-5 border-l border-line-strong pl-5">
          {data.map((e) => (
            <li key={e.id} className="relative">
              <span className="absolute -left-[25px] top-1.5 size-2 rounded-full bg-accent" aria-hidden />
              <div className="flex flex-wrap items-baseline justify-between gap-x-3">
                <span className="text-sm font-medium">{ACTION_LABEL[e.action] ?? e.action}</span>
                <time className="font-mono text-[11px] text-dim" dateTime={e.createdAt}>
                  {formatDateTime(e.createdAt)}
                </time>
              </div>
              <p className="font-mono text-[11px] text-dim">{e.userName}</p>
              <DetailLines details={e.details} />
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
