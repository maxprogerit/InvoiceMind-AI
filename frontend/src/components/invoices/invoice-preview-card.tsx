"use client";

import { AlertTriangle, ArrowUpRight, FileX2, Sparkles } from "lucide-react";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { Card, CardLabel } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/misc";
import { useInvoice } from "@/lib/api/hooks";
import { formatDate, formatMoney, formatPercent } from "@/lib/format";

import { DocumentViewer } from "./document-viewer";
import { StatusBadge } from "./status-badge";

function Row({ label, value, mono }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-2">
      <span className="text-[15px] text-muted">{label}</span>
      <span className={mono ? "truncate font-mono text-sm" : "truncate text-[15px] font-medium"}>{value}</span>
    </div>
  );
}

/** "INVOICE DETAILS" card from the mockup: live document preview and the key fields of the selected invoice. */
export function InvoicePreviewCard({ invoiceId }: { invoiceId: string | undefined }) {
  const { data, isLoading, isError } = useInvoice(invoiceId);

  return (
    <Card className="p-6">
      <CardLabel>Invoice details</CardLabel>

      {!invoiceId && (
        <div className="flex flex-col items-center gap-3 py-16 text-center text-sm text-dim">
          <FileX2 className="size-6" />
          Select an invoice to see its details.
        </div>
      )}

      {invoiceId && isLoading && (
        <div className="mt-4 space-y-3">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-6 w-full" />
          <Skeleton className="h-6 w-full" />
        </div>
      )}

      {invoiceId && isError && <p className="mt-6 text-sm text-bad">This invoice could not be loaded.</p>}

      {data && (
        <>
          <div className="mt-4 h-44 overflow-hidden rounded-control bg-[#ece6e6]">
            <div className="pointer-events-none h-full w-full">
              <DocumentViewer invoiceId={data.id} filename={data.originalFilename} chrome={false} />
            </div>
          </div>

          <div className="mt-5 flex items-center justify-between gap-3">
            <StatusBadge status={data.status} />
            {data.ocr.confidence != null && (
              <span className="flex items-center gap-1.5 font-mono text-[11px] text-dim" title="Mean OCR confidence">
                <Sparkles className="size-3" /> OCR {formatPercent(Number(data.ocr.confidence))}
              </span>
            )}
          </div>

          <div className="mt-3 divide-y divide-line/60">
            <Row label="Vendor" value={data.vendorName ?? "—"} />
            <Row label={data.documentType === "CREDIT_NOTE" ? "Credit note number" : "Invoice number"} value={data.invoiceNumber ?? "—"} />
            <Row label="Date" value={formatDate(data.invoiceDate)} />
            {data.dueDate && <Row label="Due" value={formatDate(data.dueDate)} />}
          </div>

          <div className="mt-2 flex items-baseline justify-between border-t border-line-strong pt-4">
            <span className="text-lg font-bold">Total</span>
            <span className="num text-[22px] font-bold text-accent">{formatMoney(data.total, data.currency)}</span>
          </div>

          {data.issues.length > 0 && (
            <p className="mt-4 flex items-start gap-2 rounded-control border border-warn/30 bg-warn/10 px-3 py-2 text-[13px] text-warn">
              <AlertTriangle className="mt-0.5 size-3.5 shrink-0" />
              {data.issues.length} validation {data.issues.length === 1 ? "issue" : "issues"} to look at
            </p>
          )}
          {data.status === "FAILED" && data.failureReason && <p className="mt-4 text-[13px] text-bad">{data.failureReason}</p>}

          <Button asChild className="mt-5 w-full" variant={data.permissions.canEdit || data.permissions.canApprove ? "default" : "outline"}>
            <Link href={`/invoices/${data.id}`}>
              {data.permissions.canEdit ? "Review & edit" : "Open invoice"} <ArrowUpRight />
            </Link>
          </Button>
        </>
      )}
    </Card>
  );
}
