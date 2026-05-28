"use client";

import { useQuery } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { SkeletonRow } from "@/components/ui/skeleton";
import { dashboardApi } from "@/services/dashboard-api";
import { statusBadge } from "@/components/ui/badge";

export function LiveExecutions() {
  const { data, isLoading } = useQuery({
    queryKey: ["dashboard-overview"],
    queryFn: dashboardApi.overview,
    refetchInterval: 8000,
  });

  const executions = (data?.workflowActivity ?? []) as Array<{
    id: number; status: string; durationMs?: number; progress?: number; traceId?: string;
  }>;

  return (
    <Card className="p-5">
      <h3 className="text-lg font-semibold text-foreground">Live execution monitor</h3>
      <div className="mt-4 space-y-3">
        {isLoading ? (
          Array.from({ length: 4 }).map((_, i) => <SkeletonRow key={i} />)
        ) : executions.length === 0 ? (
          <p className="py-4 text-center text-sm text-slate-500">No executions</p>
        ) : (
          executions.map((exec) => (
            <div key={exec.id} className="rounded-xl border border-border/60 bg-black/20 p-3">
              <div className="flex items-center justify-between">
                <p className="font-medium text-foreground">Execution #{exec.id}</p>
                {statusBadge(exec.status)}
              </div>
              <p className="mt-1 text-xs text-slate-400">
                {exec.traceId ?? "—"} • {exec.durationMs ?? 0}ms • {exec.progress ?? 100}%
              </p>
              {exec.progress != null && exec.progress < 100 && (
                <div className="mt-2 h-1.5 rounded-full bg-slate-800">
                  <div className="h-1.5 rounded-full bg-cyan-500" style={{ width: `${exec.progress}%` }} />
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </Card>
  );
}

