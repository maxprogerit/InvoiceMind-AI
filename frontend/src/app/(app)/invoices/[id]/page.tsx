"use client";

import { ArrowLeft, CheckCircle2, Loader2, RotateCw, Trash2, XCircle } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { AuditTimeline } from "@/components/invoices/audit-timeline";
import { DecisionDialog } from "@/components/invoices/decision-dialog";
import { DocumentViewer } from "@/components/invoices/document-viewer";
import { IssuesPanel } from "@/components/invoices/issues-panel";
import { PipelineStepper } from "@/components/invoices/pipeline-stepper";
import { ReviewForm } from "@/components/invoices/review-form";
import { StatusBadge } from "@/components/invoices/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardLabel } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/misc";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ApiError } from "@/lib/api/client";
import { useDeleteInvoice, useInvoice, useOcrText, useRetry } from "@/lib/api/hooks";
import type { InvoiceDetail } from "@/lib/api/types";
import { formatBytes, formatDateTime, formatDuration, formatPercent } from "@/lib/format";

function Meta({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-1.5">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="truncate text-right font-mono text-[13px]">{children}</dd>
    </div>
  );
}

function ProcessingCard({ inv }: { inv: InvoiceDetail }) {
  return (
    <Card className="p-6">
      <CardLabel>Processing</CardLabel>
      <dl className="mt-3 divide-y divide-line/60">
        <Meta label="File">{inv.originalFilename}</Meta>
        <Meta label="Size">{formatBytes(inv.fileSize)}</Meta>
        <Meta label="Uploaded by">{inv.uploadedByName}</Meta>
        <Meta label="Uploaded">{formatDateTime(inv.createdAt)}</Meta>
        <Meta label="Read with">
          {inv.ocr.engine ? `${inv.ocr.engine} · ${inv.ocr.pageCount ?? "?"} p · ${formatPercent(Number(inv.ocr.confidence ?? 0))}` : "—"}
        </Meta>
        <Meta label="AI model">{inv.usage.model ?? "—"}</Meta>
        <Meta label="Tokens">{inv.usage.inputTokens != null ? `${inv.usage.inputTokens} in · ${inv.usage.outputTokens} out` : "—"}</Meta>
        <Meta label="AI cost">{inv.usage.costUsd != null ? `$${Number(inv.usage.costUsd).toFixed(5)}` : "—"}</Meta>
        <Meta label="Total time">{formatDuration(inv.processingMs != null ? inv.processingMs / 1000 : null)}</Meta>
        {inv.decidedAt && <Meta label={inv.status === "APPROVED" ? "Approved by" : "Decided by"}>{inv.decidedByName}</Meta>}
        {inv.decisionComment && (
          <div className="py-2">
            <dt className="text-sm text-muted">Comment</dt>
            <dd className="mt-1 text-sm">{inv.decisionComment}</dd>
          </div>
        )}
      </dl>
    </Card>
  );
}

function OcrTextPanel({ id, active }: { id: string; active: boolean }) {
  const { data, isLoading } = useOcrText(id, active);
  if (isLoading) return <Skeleton className="h-64" />;
  return (
    <div className="rounded-control border border-line bg-bg/60">
      <pre className="max-h-[calc(100dvh-16rem)] min-h-64 overflow-auto whitespace-pre-wrap break-words p-4 font-mono text-xs leading-relaxed text-muted">
        {data?.text?.trim() || "No text was extracted yet."}
      </pre>
    </div>
  );
}

export default function InvoiceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { data: inv, isLoading, error } = useInvoice(id);
  const retry = useRetry(id);
  const remove = useDeleteInvoice(id);
  const [dialog, setDialog] = useState<"approve" | "reject" | "delete" | null>(null);
  const [tab, setTab] = useState("document");

  if (isLoading) {
    return (
      <div className="grid gap-6 xl:grid-cols-2">
        <Skeleton className="h-[70dvh]" />
        <Skeleton className="h-[70dvh]" />
      </div>
    );
  }
  if (error || !inv) {
    const notFound = error instanceof ApiError && error.status === 404;
    return (
      <div className="mx-auto mt-24 max-w-md text-center">
        <h1 className="text-2xl font-bold">{notFound ? "Invoice not found" : "Could not load this invoice"}</h1>
        <p className="mt-2 text-muted">{notFound ? "It may have been deleted, or you do not have access to it." : error?.message}</p>
        <Button asChild className="mt-6" variant="outline">
          <Link href="/invoices">
            <ArrowLeft /> Back to invoices
          </Link>
        </Button>
      </div>
    );
  }

  const p = inv.permissions;
  const title = inv.vendorName ?? inv.originalFilename;
  const summary = `${title}${inv.invoiceNumber ? ` · ${inv.invoiceNumber}` : ""}`;

  return (
    <>
      <Link href="/invoices" className="mb-4 inline-flex items-center gap-1.5 font-mono text-xs text-dim transition-colors hover:text-accent">
        <ArrowLeft className="size-3.5" /> Invoices
      </Link>

      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="truncate text-[28px] font-bold leading-tight tracking-tight">{title}</h1>
            <StatusBadge status={inv.status} className="mt-1" />
          </div>
          <p className="mt-1 font-mono text-sm text-dim">{inv.invoiceNumber ?? "no invoice number"}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {p.canRetry && (
            <Button variant="outline" disabled={retry.isPending} onClick={() => retry.mutate(undefined, { onSuccess: () => toast.success("Processing restarted"), onError: (e) => toast.error(e.message) })}>
              {retry.isPending ? <Loader2 className="animate-spin" /> : <RotateCw />} Retry processing
            </Button>
          )}
          {p.canReject && (
            <Button variant="destructive" onClick={() => setDialog("reject")}>
              <XCircle /> Reject
            </Button>
          )}
          {p.canApprove && (
            <Button variant="success" onClick={() => setDialog("approve")}>
              <CheckCircle2 /> Approve
            </Button>
          )}
          {p.canDelete && (
            <Button variant="ghost" size="icon" aria-label="Delete invoice" onClick={() => setDialog("delete")}>
              <Trash2 />
            </Button>
          )}
        </div>
      </div>

      {inv.status === "FAILED" && (
        <div className="mb-6 rounded-card border border-bad/30 bg-bad/10 px-5 py-4 text-sm text-bad" role="alert">
          <strong>Processing failed.</strong> {inv.failureReason ?? "Unknown error."} {p.canRetry && "You can retry once the problem is resolved."}
        </div>
      )}
      {inv.status === "VALIDATED" && !p.canApprove && p.canEdit && (
        <p className="mb-6 text-sm text-muted">This invoice passed all checks and is waiting for an approver.</p>
      )}

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
        <div className="xl:sticky xl:top-6">
          <Card className="overflow-hidden p-4">
            <Tabs value={tab} onValueChange={setTab}>
              <TabsList>
                <TabsTrigger value="document">Document</TabsTrigger>
                <TabsTrigger value="text">Recognised text</TabsTrigger>
              </TabsList>
              <TabsContent value="document" className="h-[calc(100dvh-15rem)] min-h-[480px]">
                <DocumentViewer invoiceId={inv.id} filename={inv.originalFilename} />
              </TabsContent>
              <TabsContent value="text">
                <OcrTextPanel id={inv.id} active={tab === "text"} />
              </TabsContent>
            </Tabs>
          </Card>
        </div>

        <div className="space-y-6">
          <Card className="p-6">
            <CardLabel className="mb-5">Progress</CardLabel>
            <PipelineStepper status={inv.status} />
          </Card>
          <IssuesPanel invoice={inv} />
          <ReviewForm invoice={inv} />
          <ProcessingCard inv={inv} />
          <AuditTimeline invoiceId={inv.id} />
        </div>
      </div>

      <DecisionDialog invoiceId={inv.id} kind="approve" summary={summary} open={dialog === "approve"} onOpenChange={(o) => setDialog(o ? "approve" : null)} />
      <DecisionDialog invoiceId={inv.id} kind="reject" summary={summary} open={dialog === "reject"} onOpenChange={(o) => setDialog(o ? "reject" : null)} />

      <Dialog open={dialog === "delete"} onOpenChange={(o) => setDialog(o ? "delete" : null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete this invoice?</DialogTitle>
            <DialogDescription>
              {summary} and its stored file will be removed permanently. The deletion itself stays in the audit log.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setDialog(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={remove.isPending}
              onClick={() =>
                remove.mutate(undefined, {
                  onSuccess: () => {
                    toast.success("Invoice deleted");
                    router.replace("/invoices");
                  },
                  onError: (e) => toast.error(e.message),
                })
              }
            >
              {remove.isPending && <Loader2 className="animate-spin" />} Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
