"use client";

import { useQuery } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { SkeletonCard } from "@/components/ui/skeleton";
import { dashboardApi } from "@/services/dashboard-api";
import { motion } from "framer-motion";

export function KpiCards() {
  const { data, isLoading } = useQuery({
    queryKey: ["dashboard-overview"],
    queryFn: dashboardApi.overview,
    refetchInterval: 15000,
  });

  if (isLoading) {
    return (
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => <SkeletonCard key={i} />)}
      </div>
    );
  }

  const kpis = data?.kpis;
  const items = [
    { label: "Documents Processed", value: kpis?.documentsProcessed ?? "—", change: "All time" },
    { label: "Automation Rate", value: kpis?.automationRate ?? "—", change: "This month" },
    { label: "Invoice Volume", value: kpis?.invoiceVolume ?? "—", change: "Total value" },
    { label: "Pending Approvals", value: kpis?.pendingApprovals ?? "—", change: "Needs review" },
  ];

  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {items.map((kpi, index) => (
        <motion.div
          key={kpi.label}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: index * 0.05, duration: 0.35 }}
        >
          <Card className="p-5">
            <p className="text-xs uppercase tracking-wide text-cyan-300/70">{kpi.label}</p>
            <p className="mt-2 text-3xl font-semibold text-foreground">{kpi.value}</p>
            <p className="mt-1 text-sm text-emerald-400">{kpi.change}</p>
          </Card>
        </motion.div>
      ))}
    </div>
  );
}

