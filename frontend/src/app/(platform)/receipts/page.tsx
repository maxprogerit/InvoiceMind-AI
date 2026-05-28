"use client";

import { useCallback, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { SkeletonRow } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { statusBadge } from "@/components/ui/badge";
import { useNotificationStore } from "@/store/notification-store";
import { receiptsApi } from "@/services/receipts-api";
import { Upload } from "lucide-react";

export default function ReceiptsPage() {
  const qc = useQueryClient();
  const { success, error } = useNotificationStore();
  const [dragging, setDragging] = useState(false);

  const { data: receipts = [], isLoading } = useQuery({
    queryKey: ["receipts"],
    queryFn: receiptsApi.list,
    refetchInterval: 12000,
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => receiptsApi.upload(file),
    onSuccess: () => {
      success("Receipt uploaded and categorized");
      qc.invalidateQueries({ queryKey: ["receipts"] });
    },
    onError: (e: Error) => error(e.message),
  });

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    Array.from(e.dataTransfer.files).forEach((f) => uploadMutation.mutate(f));
  }, [uploadMutation]);

  const total = receipts.length;
  const totalAmt = receipts.reduce((s: number, r: any) => s + (Number(r.totalAmount) || 0), 0);

  return (
    <PageShell title="Receipts" subtitle="Upload expense receipts and let AI categorize merchant spend automatically.">
      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <div className="rounded-xl border border-border/40 p-4">
          <p className="text-xs text-slate-500">Total receipts</p>
          <p className="mt-1 text-2xl font-bold text-foreground">{total}</p>
        </div>
        <div className="rounded-xl border border-border/40 p-4">
          <p className="text-xs text-slate-500">Total amount</p>
          <p className="mt-1 text-2xl font-bold text-foreground">${totalAmt.toLocaleString()}</p>
        </div>
        <div className="rounded-xl border border-border/40 p-4">
          <p className="text-xs text-slate-500">Processed</p>
          <p className="mt-1 text-2xl font-bold text-foreground">
            {receipts.filter((r: any) => r.status === "PROCESSED").length}
          </p>
        </div>
      </div>

      <div
        onDrop={onDrop}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        className={`mb-6 rounded-xl border-2 border-dashed p-8 text-center transition-colors ${
          dragging ? "border-cyan-400 bg-cyan-500/5" : "border-cyan-300/40"
        }`}
      >
        <Upload className="mx-auto mb-2 h-7 w-7 text-slate-500" />
        <p className="text-sm text-slate-400">
          Drop receipts here or{" "}
          <label className="cursor-pointer text-cyan-300 hover:text-cyan-200">
            browse
            <input type="file" multiple accept=".pdf,.png,.jpg" className="hidden"
              onChange={(e) => Array.from(e.target.files ?? []).forEach((f) => uploadMutation.mutate(f))} />
          </label>
        </p>
        {uploadMutation.isPending && <p className="mt-1 text-xs text-cyan-400 animate-pulse">Uploading…</p>}
      </div>

      {isLoading ? (
        <div className="space-y-2">{Array.from({ length: 5 }).map((_, i) => <SkeletonRow key={i} />)}</div>
      ) : receipts.length === 0 ? (
        <EmptyState title="No receipts yet" description="Upload your first expense receipt." />
      ) : (
        <div className="space-y-2">
          {receipts.map((r: any) => (
            <div key={r.id} className="flex items-center gap-4 rounded-xl border border-border/40 p-4 text-sm">
              <div className="flex-1">
                <p className="font-medium text-foreground">{r.merchantName ?? "Unknown merchant"}</p>
                <p className="text-xs text-slate-500">
                  {r.merchantCategory ?? "Uncategorized"}
                  {r.receiptDate && ` • ${r.receiptDate}`}
                </p>
              </div>
              <span className="font-semibold text-foreground">
                ${Number(r.totalAmount ?? 0).toLocaleString()}
              </span>
              {statusBadge(r.status)}
            </div>
          ))}
        </div>
      )}
    </PageShell>
  );
}

