"use client";

import { AlertCircle, ArrowRight, FileText, Loader2, RotateCw, UploadCloud, X } from "lucide-react";
import Link from "next/link";
import { useCallback, useRef, useState } from "react";
import { useDropzone, type FileRejection } from "react-dropzone";
import { toast } from "sonner";

import { PipelineStepper } from "@/components/invoices/pipeline-stepper";
import { StatusBadge } from "@/components/invoices/status-badge";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardLabel } from "@/components/ui/card";
import { Progress } from "@/components/ui/misc";
import { ApiError, uploadInvoice } from "@/lib/api/client";
import { useInvoice, useRetry } from "@/lib/api/hooks";
import { formatBytes, formatMoney } from "@/lib/format";
import { cn } from "@/lib/utils";

const MAX_BYTES = 15 * 1024 * 1024;
const ACCEPT = {
  "application/pdf": [".pdf"],
  "image/png": [".png"],
  "image/jpeg": [".jpg", ".jpeg"],
  "image/tiff": [".tif", ".tiff"],
  "image/webp": [".webp"],
};

interface Item {
  key: string;
  file: File;
  progress: number;
  phase: "queued" | "uploading" | "done" | "error";
  invoiceId?: string;
  error?: string;
}

const uid = () => Math.random().toString(36).slice(2, 10);

export default function UploadPage() {
  const [items, setItems] = useState<Item[]>([]);
  const running = useRef(0);
  const queue = useRef<string[]>([]);
  const itemsRef = useRef<Item[]>([]);
  itemsRef.current = items;

  const patch = useCallback((key: string, change: Partial<Item>) => setItems((all) => all.map((i) => (i.key === key ? { ...i, ...change } : i))), []);

  const pump = useCallback(() => {
    while (running.current < 2 && queue.current.length > 0) {
      const key = queue.current.shift()!;
      const item = itemsRef.current.find((i) => i.key === key);
      if (!item) continue;
      running.current++;
      patch(key, { phase: "uploading", progress: 0, error: undefined });
      uploadInvoice(item.file, (p) => patch(key, { progress: p }))
        .then(({ id }) => patch(key, { phase: "done", progress: 1, invoiceId: id }))
        .catch((e: unknown) => patch(key, { phase: "error", error: e instanceof ApiError ? e.message : "Upload failed" }))
        .finally(() => {
          running.current--;
          pump();
        });
    }
  }, [patch]);

  const enqueue = useCallback(
    (files: File[]) => {
      const added = files.map<Item>((file) => ({ key: uid(), file, progress: 0, phase: "queued" }));
      itemsRef.current = [...itemsRef.current, ...added];
      setItems(itemsRef.current);
      queue.current.push(...added.map((a) => a.key));
      pump();
    },
    [pump],
  );

  const onDrop = useCallback(
    (accepted: File[], rejected: FileRejection[]) => {
      for (const r of rejected) {
        const reason = r.errors[0]?.code === "file-too-large" ? "is larger than 15 MB" : r.errors[0]?.code === "file-invalid-type" ? "is not a PDF or image" : "was rejected";
        toast.error(`${r.file.name} ${reason}`);
      }
      if (accepted.length) enqueue(accepted);
    },
    [enqueue],
  );

  const { getRootProps, getInputProps, isDragActive, open } = useDropzone({ onDrop, accept: ACCEPT, maxSize: MAX_BYTES, noClick: true, multiple: true });

  const retryUpload = (key: string) => {
    queue.current.push(key);
    pump();
  };

  return (
    <>
      <PageHeader
        title="Upload"
        subtitle="Drop invoices here. They are read, extracted and validated in the background."
        actions={
          <Button asChild variant="outline">
            <Link href="/invoices">
              All invoices <ArrowRight />
            </Link>
          </Button>
        }
      />

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(300px,1fr)]">
        <div className="space-y-5">
          <div
            {...getRootProps()}
            className={cn(
              "group relative flex min-h-64 flex-col items-center justify-center gap-4 overflow-hidden rounded-card border-2 border-dashed px-6 py-12 text-center transition-all",
              isDragActive ? "scale-[1.01] border-accent bg-accent-soft/40" : "border-line-strong bg-surface hover:border-accent/50",
            )}
          >
            <input {...getInputProps()} aria-label="Choose invoice files" />
            <div className={cn("grid size-16 place-items-center rounded-2xl border transition-colors", isDragActive ? "border-accent bg-accent-soft text-accent" : "border-line-strong bg-surface-2 text-muted group-hover:text-accent")}>
              <UploadCloud className="size-7" />
            </div>
            <div>
              <p className="text-xl font-semibold">{isDragActive ? "Release to upload" : "Drag and drop invoices"}</p>
              <p className="mt-1.5 font-mono text-xs text-dim">PDF · PNG · JPG · TIFF · WEBP — up to 15 MB each</p>
            </div>
            <Button onClick={open} size="lg">
              Browse files
            </Button>
          </div>

          {items.length > 0 && (
            <ul className="space-y-3" aria-label="Uploads">
              {items.map((item) => (
                <UploadRow
                  key={item.key}
                  item={item}
                  onRemove={() => setItems((all) => all.filter((i) => i.key !== item.key))}
                  onRetryUpload={() => retryUpload(item.key)}
                />
              ))}
            </ul>
          )}
        </div>

        <Card className="p-6">
          <CardLabel>What happens next</CardLabel>
          <ol className="mt-5 space-y-5">
            {[
              ["Read", "PDFs with a text layer are read directly; scans and photos go through Tesseract OCR."],
              ["Extract", "An AI model turns the text into vendor, dates, line items and totals. Nothing is invented: every value must appear in the document."],
              ["Validate", "Deterministic checks verify sums, tax, dates, currency and duplicates. Anything doubtful is sent to review."],
              ["Approve", "An approver signs off. Every step is recorded in the audit log."],
            ].map(([title, text], i) => (
              <li key={title} className="flex gap-4">
                <span className="grid size-7 shrink-0 place-items-center rounded-full border border-line-strong font-mono text-xs text-accent">{i + 1}</span>
                <div>
                  <div className="font-semibold">{title}</div>
                  <p className="mt-0.5 text-sm leading-relaxed text-muted">{text}</p>
                </div>
              </li>
            ))}
          </ol>
        </Card>
      </div>
    </>
  );
}

function UploadRow({ item, onRemove, onRetryUpload }: { item: Item; onRemove: () => void; onRetryUpload: () => void }) {
  const live = useInvoice(item.invoiceId);
  const retry = useRetry(item.invoiceId ?? "");
  const inv = live.data;
  const failedStatus = inv?.status === "FAILED";

  return (
    <li className="rounded-card border border-line bg-surface p-4">
      <div className="flex items-start gap-3">
        <div className="grid size-10 shrink-0 place-items-center rounded-control bg-surface-2 text-muted">
          <FileText className="size-5" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-3">
            <p className="truncate font-medium">{item.file.name}</p>
            <div className="flex shrink-0 items-center gap-2">
              {inv && <StatusBadge status={inv.status} />}
              {item.phase === "uploading" && (
                <span className="flex items-center gap-1.5 font-mono text-xs text-muted">
                  <Loader2 className="size-3.5 animate-spin" /> {Math.round(item.progress * 100)}%
                </span>
              )}
              {item.phase !== "uploading" && (
                <button onClick={onRemove} aria-label={`Remove ${item.file.name} from the list`} className="rounded p-1 text-dim hover:bg-surface-2 hover:text-fg">
                  <X className="size-4" />
                </button>
              )}
            </div>
          </div>
          <p className="font-mono text-[11px] text-dim">{formatBytes(item.file.size)}</p>

          {(item.phase === "uploading" || item.phase === "queued") && (
            <Progress value={item.phase === "queued" ? 0 : item.progress * 100} className="mt-3" />
          )}

          {item.phase === "error" && (
            <div className="mt-3 flex items-center justify-between gap-3 rounded-control border border-bad/30 bg-bad/10 px-3 py-2 text-sm text-bad">
              <span className="flex items-center gap-2">
                <AlertCircle className="size-4 shrink-0" /> {item.error}
              </span>
              <Button size="sm" variant="outline" onClick={onRetryUpload}>
                <RotateCw /> Retry
              </Button>
            </div>
          )}

          {item.phase === "done" && inv && (
            <div className="mt-4 space-y-4">
              <PipelineStepper status={inv.status} className="max-w-md" />
              {failedStatus && (
                <div className="flex items-center justify-between gap-3 rounded-control border border-bad/30 bg-bad/10 px-3 py-2 text-sm text-bad">
                  <span>{inv.failureReason ?? "Processing failed"}</span>
                  <Button size="sm" variant="outline" disabled={retry.isPending} onClick={() => retry.mutate(undefined, { onError: (e) => toast.error(e.message) })}>
                    <RotateCw className={cn(retry.isPending && "animate-spin")} /> Retry
                  </Button>
                </div>
              )}
              {(inv.status === "VALIDATED" || inv.status === "NEEDS_REVIEW") && (
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <p className="text-sm text-muted">
                    {inv.vendorName ?? "Unknown vendor"}
                    {inv.invoiceNumber ? ` · ${inv.invoiceNumber}` : ""}
                    {inv.total != null && <span className="num ml-2 font-semibold text-fg">{formatMoney(inv.total, inv.currency)}</span>}
                  </p>
                  <Button asChild size="sm" variant={inv.status === "NEEDS_REVIEW" ? "default" : "outline"}>
                    <Link href={`/invoices/${inv.id}`}>
                      {inv.status === "NEEDS_REVIEW" ? "Review now" : "Open"} <ArrowRight />
                    </Link>
                  </Button>
                </div>
              )}
            </div>
          )}
          {item.phase === "done" && !inv && live.isLoading && <p className="mt-3 font-mono text-xs text-dim">Queued…</p>}
        </div>
      </div>
    </li>
  );
}
