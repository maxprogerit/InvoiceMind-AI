"use client";

import { useQuery } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { SkeletonRow } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { historyApi } from "@/services/history-api";

const ACTION_COLOR: Record<string, string> = {
  UPLOAD: "text-cyan-400",
  OCR_COMPLETED: "text-emerald-400",
  APPROVED: "text-green-400",
  REJECTED: "text-rose-400",
  DELETED: "text-red-400",
  EXPORT: "text-violet-400",
};

export default function HistoryPage() {
  const { data = [], isLoading } = useQuery({
    queryKey: ["history"],
    queryFn: () => historyApi.list(0, 50),
    refetchInterval: 15000,
  });

  return (
    <PageShell title="History" subtitle="Audit trail, user actions, exports, AI execution records, and errors.">
      {isLoading ? (
        <div className="space-y-2">{Array.from({ length: 8 }).map((_, i) => <SkeletonRow key={i} />)}</div>
      ) : data.length === 0 ? (
        <EmptyState title="No history yet" description="Actions will appear here as you use the platform." />
      ) : (
        <div className="space-y-1">
          {data.map((entry: any, i: number) => (
            <div key={i} className="flex items-start gap-3 rounded-lg px-3 py-2.5 text-sm hover:bg-slate-800/30">
              <span className="mt-0.5 w-[7rem] shrink-0 text-xs text-slate-500">
                {new Date(entry.createdAt).toLocaleTimeString()}
              </span>
              <span className={`shrink-0 text-xs font-semibold uppercase ${ACTION_COLOR[entry.action] ?? "text-slate-400"}`}>
                {entry.action}
              </span>
              <span className="text-slate-300">
                {entry.description ?? (entry.metadataJson ? entry.metadataJson.substring(0, 80) : `${entry.action} on ${entry.entityType} #${entry.entityId ?? "—"}`)}
              </span>
              <span className="ml-auto shrink-0 text-xs text-slate-600 capitalize">{entry.entityType}</span>
            </div>
          ))}
        </div>
      )}
    </PageShell>
  );
}

