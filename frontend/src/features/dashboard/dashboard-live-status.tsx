"use client";

import { useQuery } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { apiGet } from "@/services/api";

export function DashboardLiveStatus() {
  const { data, isError } = useQuery({
    queryKey: ["dashboard-overview"],
    queryFn: () => apiGet<{ kpis: Record<string, string> }>("/dashboard/overview")
  });

  return (
    <Card className="p-4">
      <p className="text-xs uppercase tracking-wide text-cyan-300/80">Backend link</p>
      <p className="mt-1 text-sm text-foreground">
        {isError ? "Using resilient mock data mode (backend unavailable)." : "Connected to live Spring API."}
      </p>
      {data?.kpis ? <p className="mt-1 text-xs text-slate-400">Server automation rate: {data.kpis.automationRate}</p> : null}
    </Card>
  );
}
