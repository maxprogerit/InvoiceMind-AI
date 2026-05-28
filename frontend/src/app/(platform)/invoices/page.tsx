"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { Button } from "@/components/ui/button";
import { SkeletonRow } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { statusBadge } from "@/components/ui/badge";
import { Drawer } from "@/components/ui/drawer";
import { useNotificationStore } from "@/store/notification-store";
import { invoicesApi, Invoice } from "@/services/invoices-api";
import { Download, Upload } from "lucide-react";

export default function InvoicesPage() {
  const qc = useQueryClient();
  const { success, error } = useNotificationStore();
  const [selected, setSelected] = useState<Invoice | null>(null);

  const { data: invoices = [], isLoading } = useQuery({
    queryKey: ["invoices"],
    queryFn: invoicesApi.list,
    refetchInterval: 15000,
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      invoicesApi.updateStatus(id, status),
    onSuccess: () => {
      success("Status updated");
      qc.invalidateQueries({ queryKey: ["invoices"] });
      qc.invalidateQueries({ queryKey: ["dashboard-overview"] });
      qc.invalidateQueries({ queryKey: ["approvals"] });
    },
    onError: (e: Error) => error(e.message),
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => invoicesApi.upload(file),
    onSuccess: (r) => {
      success(`Invoice processed — ID #${r.invoiceId}`);
      qc.invalidateQueries({ queryKey: ["invoices"] });
    },
    onError: (e: Error) => error(e.message),
  });

  return (
    <PageShell title="Invoices" subtitle="Track extraction, validation, approval, and export lifecycle.">
      <div className="mb-4 flex gap-3">
        <label className="cursor-pointer">
          <Button className="pointer-events-none" disabled={uploadMutation.isPending}>
            <Upload className="mr-2 h-4 w-4" />
            {uploadMutation.isPending ? "Processing…" : "Upload Invoice"}
          </Button>
          <input type="file" accept=".pdf,.png,.jpg" className="hidden"
            onChange={(e) => { if (e.target.files?.[0]) uploadMutation.mutate(e.target.files[0]); }} />
        </label>
        <Button onClick={() => invoicesApi.export().catch((e) => error(e.message))}
          className="bg-slate-700 text-slate-200 hover:bg-slate-600">
          <Download className="mr-2 h-4 w-4" /> Export CSV
        </Button>
      </div>

      <div className="overflow-x-auto">
        {isLoading ? (
          <div className="space-y-2">{Array.from({ length: 8 }).map((_, i) => <SkeletonRow key={i} />)}</div>
        ) : invoices.length === 0 ? (
          <EmptyState title="No invoices yet" description="Upload an invoice to get started." />
        ) : (
          <table className="w-full min-w-[780px] text-sm">
            <thead className="text-slate-400">
              <tr>
                <th className="py-2 text-left">Invoice</th>
                <th className="py-2 text-left">Vendor</th>
                <th className="py-2 text-left">Amount</th>
                <th className="py-2 text-left">Due date</th>
                <th className="py-2 text-left">Category</th>
                <th className="py-2 text-left">Confidence</th>
                <th className="py-2 text-left">Status</th>
                <th className="py-2 text-left">Actions</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.id} className="border-t border-border/40 hover:bg-slate-800/20 cursor-pointer">
                  <td className="py-3">
                    <button onClick={() => setSelected(inv)} className="text-cyan-300 hover:text-cyan-200">
                      {inv.invoiceNumber}
                    </button>
                  </td>
                  <td>{inv.vendorName}</td>
                  <td>{inv.currency} {Number(inv.totalAmount).toLocaleString()}</td>
                  <td>{inv.dueDate ?? "—"}</td>
                  <td>{inv.smartCategory ?? "—"}</td>
                  <td>{inv.confidenceScore != null ? (Number(inv.confidenceScore) * 100).toFixed(1) + "%" : "—"}</td>
                  <td>{statusBadge(inv.status)}</td>
                  <td>
                    {inv.status === "processing" && (
                      <Button onClick={() => statusMutation.mutate({ id: inv.id, status: "approved" })}
                        className="h-7 px-3 text-xs">
                        Approve
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <Drawer open={!!selected} onClose={() => setSelected(null)} title="Invoice Details">
        {selected && (
          <div className="space-y-4 text-sm">
            <div><span className="text-slate-500">Invoice #:</span><p className="mt-0.5 font-mono text-foreground">{selected.invoiceNumber}</p></div>
            <div><span className="text-slate-500">Vendor:</span><p className="mt-0.5 text-foreground">{selected.vendorName}</p></div>
            <div><span className="text-slate-500">Amount:</span><p className="mt-0.5 text-foreground">{selected.currency} {Number(selected.totalAmount).toLocaleString()}</p></div>
            <div><span className="text-slate-500">Due Date:</span><p className="mt-0.5 text-foreground">{selected.dueDate ?? "—"}</p></div>
            <div><span className="text-slate-500">Category:</span><p className="mt-0.5 text-foreground">{selected.smartCategory ?? "—"}</p></div>
            <div><span className="text-slate-500">Confidence:</span><p className="mt-0.5 text-foreground">{selected.confidenceScore != null ? (Number(selected.confidenceScore) * 100).toFixed(1) + "%" : "—"}</p></div>
            <div><span className="text-slate-500">Status:</span><p className="mt-0.5">{statusBadge(selected.status)}</p></div>
            <div className="flex gap-2 pt-2">
              {selected.status !== "approved" && (
                <Button onClick={() => { statusMutation.mutate({ id: selected.id, status: "approved" }); setSelected(null); }}>
                  Approve
                </Button>
              )}
              {selected.status !== "rejected" && (
                <Button onClick={() => { statusMutation.mutate({ id: selected.id, status: "rejected" }); setSelected(null); }}
                  className="border-rose-400/40 bg-rose-500/15 text-rose-300">
                  Reject
                </Button>
              )}
            </div>
          </div>
        )}
      </Drawer>
    </PageShell>
  );
}

