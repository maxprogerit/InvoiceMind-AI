"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { Button } from "@/components/ui/button";
import { SkeletonRow } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { statusBadge } from "@/components/ui/badge";
import { Modal } from "@/components/ui/modal";
import { useNotificationStore } from "@/store/notification-store";
import { reportsApi, Report } from "@/services/reports-api";
import { Plus, Download } from "lucide-react";

const REPORT_TYPES = ["MONTHLY_INVOICE_SUMMARY", "VENDOR_REPORT", "AUTOMATION_PERFORMANCE"];

export default function ReportsPage() {
  const qc = useQueryClient();
  const { success, error } = useNotificationStore();
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState({ type: REPORT_TYPES[0], dateFrom: "", dateTo: "" });

  const { data: reports = [], isLoading } = useQuery({
    queryKey: ["reports"],
    queryFn: reportsApi.list,
    refetchInterval: 15000,
  });

  const generateMutation = useMutation({
    mutationFn: () => reportsApi.generate({ reportType: form.type, dateFrom: form.dateFrom || undefined, dateTo: form.dateTo || undefined }),
    onSuccess: () => {
      success("Report generation started");
      setCreateOpen(false);
      qc.invalidateQueries({ queryKey: ["reports"] });
    },
    onError: (e: Error) => error(e.message),
  });

  return (
    <PageShell title="Reports" subtitle="Generate exportable PDF, CSV, and Excel reports with financial summaries.">
      <div className="mb-4">
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="mr-2 h-4 w-4" /> Generate Report
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">{Array.from({ length: 4 }).map((_, i) => <SkeletonRow key={i} />)}</div>
      ) : reports.length === 0 ? (
        <EmptyState title="No reports yet" description="Click Generate Report to create your first report." />
      ) : (
        <div className="space-y-3">
          {reports.map((r: Report) => (
            <div key={r.id} className="flex items-center justify-between rounded-xl border border-border/40 p-4">
              <div>
                <p className="font-medium text-foreground">{r.reportType.replace(/_/g, " ")}</p>
                <p className="text-xs text-slate-500">
                  {r.reportType} • {r.dateFrom ?? "—"} → {r.dateTo ?? "—"}
                  {r.recordCount != null && ` • ${r.recordCount} records`}
                </p>
              </div>
              <div className="flex items-center gap-3">
                {statusBadge(r.status)}
                {r.status === "COMPLETED" && (
                  <button onClick={() => reportsApi.download(r.id).catch((e) => error(e.message))}
                    className="rounded p-1.5 text-slate-400 hover:text-cyan-300">
                    <Download className="h-4 w-4" />
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Generate Report">
        <div className="space-y-3">
          <div>
            <label className="mb-1 block text-xs text-slate-400">Report Type</label>
            <select value={form.type} onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}
              className="w-full rounded-xl border border-border/60 bg-slate-800 px-3 py-2 text-sm text-foreground">
              {REPORT_TYPES.map((t) => <option key={t}>{t}</option>)}
            </select>
          </div>
          {[{ k: "dateFrom", label: "From" }, { k: "dateTo", label: "To" }].map(({ k, label }) => (
            <div key={k}>
              <label className="mb-1 block text-xs text-slate-400">{label}</label>
              <input type="date" value={(form as any)[k]}
                onChange={(e) => setForm((f) => ({ ...f, [k]: e.target.value }))}
                className="w-full rounded-xl border border-border/60 bg-slate-800 px-3 py-2 text-sm text-foreground" />
            </div>
          ))}
          <div className="flex gap-2 pt-2">
            <Button onClick={() => generateMutation.mutate()} disabled={generateMutation.isPending}>
              {generateMutation.isPending ? "Generating…" : "Generate"}
            </Button>
            <Button onClick={() => setCreateOpen(false)} className="bg-slate-700 text-slate-200">Cancel</Button>
          </div>
        </div>
      </Modal>
    </PageShell>
  );
}

