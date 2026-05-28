"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { Button } from "@/components/ui/button";
import { SkeletonRow } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { statusBadge } from "@/components/ui/badge";
import { useNotificationStore } from "@/store/notification-store";
import { executionsApi } from "@/services/executions-api";
import { RefreshCw, XCircle } from "lucide-react";

export default function ExecutionsPage() {
  const qc = useQueryClient();
  const { success, error } = useNotificationStore();

  const { data: executions = [], isLoading } = useQuery({
    queryKey: ["executions"],
    queryFn: executionsApi.list,
    refetchInterval: 5000,
  });

  const retryMutation = useMutation({
    mutationFn: (id: number) => executionsApi.retry(id),
    onSuccess: () => {
      success("Retry queued");
      qc.invalidateQueries({ queryKey: ["executions"] });
    },
    onError: (e: Error) => error(e.message),
  });

  const cancelMutation = useMutation({
    mutationFn: (id: number) => executionsApi.cancel(id),
    onSuccess: () => {
      success("Execution cancelled");
      qc.invalidateQueries({ queryKey: ["executions"] });
    },
    onError: (e: Error) => error(e.message),
  });

  return (
    <PageShell title="Executions" subtitle="Real-time queue monitoring, retries, Kafka tracking, and duration metrics.">
      {isLoading ? (
        <div className="space-y-2">{Array.from({ length: 6 }).map((_, i) => <SkeletonRow key={i} />)}</div>
      ) : executions.length === 0 ? (
        <EmptyState title="No executions yet" description="Upload a document to start processing." />
      ) : (
        <div className="space-y-3">
          {executions.map((exec) => (
            <div key={exec.id} className="rounded-xl border border-border/40 p-4 space-y-2">
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-foreground">{exec.workflowName}</p>
                  <p className="text-xs text-slate-500">
                    Execution #{exec.id}
                    {exec.durationMs != null && ` • ${(exec.durationMs / 1000).toFixed(1)}s`}
                    {exec.createdAt && ` • ${new Date(exec.createdAt).toLocaleString()}`}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {statusBadge(exec.status)}
                  {exec.status === "FAILED" && (
                    <button onClick={() => retryMutation.mutate(exec.id)}
                      className="rounded p-1.5 text-slate-400 hover:text-cyan-300">
                      <RefreshCw className="h-4 w-4" />
                    </button>
                  )}
                  {(exec.status === "RUNNING" || exec.status === "QUEUED") && (
                    <button onClick={() => cancelMutation.mutate(exec.id)}
                      className="rounded p-1.5 text-slate-400 hover:text-rose-400">
                      <XCircle className="h-4 w-4" />
                    </button>
                  )}
                </div>
              </div>
              {exec.progressPercent != null && exec.status === "RUNNING" && (
                <div className="h-1.5 w-full rounded-full bg-slate-700">
                  <div
                    className="h-1.5 rounded-full bg-cyan-400 transition-all"
                    style={{ width: `${exec.progressPercent}%` }}
                  />
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </PageShell>
  );
}

