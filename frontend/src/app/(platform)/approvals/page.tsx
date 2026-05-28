"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { Button } from "@/components/ui/button";
import { SkeletonRow } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { statusBadge } from "@/components/ui/badge";
import { Drawer } from "@/components/ui/drawer";
import { useNotificationStore } from "@/store/notification-store";
import { approvalsApi, Approval } from "@/services/approvals-api";
import { useState } from "react";
import { CheckCircle2, XCircle } from "lucide-react";

export default function ApprovalsPage() {
  const qc = useQueryClient();
  const { success, error } = useNotificationStore();
  const [selected, setSelected] = useState<Approval | null>(null);
  const [comment, setComment] = useState("");

  const { data: approvals = [], isLoading } = useQuery({
    queryKey: ["approvals"],
    queryFn: approvalsApi.list,
    refetchInterval: 8000,
  });

  const decideMutation = useMutation({
    mutationFn: ({ id, decision, cmt }: { id: number; decision: "approved" | "rejected"; cmt?: string }) =>
      approvalsApi.decide(id, decision, cmt),
    onSuccess: (_, vars) => {
      success(vars.decision === "approved" ? "Approval granted ✓" : "Request rejected");
      setSelected(null);
      qc.invalidateQueries({ queryKey: ["approvals"] });
      qc.invalidateQueries({ queryKey: ["invoices"] });
      qc.invalidateQueries({ queryKey: ["dashboard-overview"] });
    },
    onError: (e: Error) => error(e.message),
  });

  const pending = approvals.filter((a) => a.status === "pending");
  const resolved = approvals.filter((a) => a.status !== "pending");

  return (
    <PageShell title="Approvals" subtitle="Queue pending approvals with AI recommendation and chain routing.">
      <div className="space-y-6">
        <div>
          <h3 className="mb-3 text-sm font-medium text-slate-400">
            Pending ({pending.length})
          </h3>
          {isLoading ? (
            Array.from({ length: 3 }).map((_, i) => <SkeletonRow key={i} />)
          ) : pending.length === 0 ? (
            <EmptyState title="No pending approvals" description="All caught up!" />
          ) : (
            <div className="space-y-3">
              {pending.map((a) => (
                <div key={a.id} className="flex items-center justify-between rounded-xl border border-amber-500/20 bg-amber-500/5 p-4">
                  <div className="cursor-pointer" onClick={() => { setSelected(a); setComment(""); }}>
                    <p className="font-medium text-foreground">Approval #{a.id} — Invoice #{a.invoiceId}</p>
                    <p className="text-xs text-slate-400">{a.aiRecommendation ?? "Review required"}</p>
                  </div>
                  <div className="flex gap-2">
                    <Button onClick={() => decideMutation.mutate({ id: a.id, decision: "approved" })}
                      disabled={decideMutation.isPending}
                      className="h-8 px-3 text-xs">
                      <CheckCircle2 className="mr-1 h-3.5 w-3.5" /> Approve
                    </Button>
                    <Button onClick={() => decideMutation.mutate({ id: a.id, decision: "rejected" })}
                      disabled={decideMutation.isPending}
                      className="h-8 border-rose-400/40 bg-rose-500/15 px-3 text-xs text-rose-300 hover:bg-rose-500/20">
                      <XCircle className="mr-1 h-3.5 w-3.5" /> Reject
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {resolved.length > 0 && (
          <div>
            <h3 className="mb-3 text-sm font-medium text-slate-400">History ({resolved.length})</h3>
            <div className="space-y-2">
              {resolved.map((a) => (
                <div key={a.id} className="flex items-center gap-4 rounded-xl border border-border/40 p-3 text-sm">
                  <span className="text-slate-500">#{a.id}</span>
                  <span className="flex-1 text-foreground">Invoice #{a.invoiceId}</span>
                  {statusBadge(a.status)}
                  <span className="text-xs text-slate-500">{a.decidedAt ? new Date(a.decidedAt).toLocaleDateString() : "—"}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <Drawer open={!!selected} onClose={() => setSelected(null)} title="Approval Details">
        {selected && (
          <div className="space-y-4">
            <div className="space-y-2 text-sm">
              <div><span className="text-slate-500">Approval ID:</span><p className="text-foreground">#{selected.id}</p></div>
              <div><span className="text-slate-500">Invoice ID:</span><p className="text-foreground">#{selected.invoiceId}</p></div>
              <div><span className="text-slate-500">Status:</span><p className="mt-0.5">{statusBadge(selected.status)}</p></div>
              <div><span className="text-slate-500">AI Recommendation:</span><p className="text-foreground">{selected.aiRecommendation ?? "—"}</p></div>
            </div>
            <textarea
              placeholder="Add a comment (optional)…"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              className="mt-2 w-full rounded-xl border border-border/60 bg-slate-800 p-3 text-sm text-foreground resize-none"
              rows={3}
            />
            <div className="flex gap-3">
              <Button onClick={() => decideMutation.mutate({ id: selected.id, decision: "approved", cmt: comment })}>
                Approve
              </Button>
              <Button onClick={() => decideMutation.mutate({ id: selected.id, decision: "rejected", cmt: comment })}
                className="border-rose-400/40 bg-rose-500/15 text-rose-300">
                Reject
              </Button>
            </div>
          </div>
        )}
      </Drawer>
    </PageShell>
  );
}

