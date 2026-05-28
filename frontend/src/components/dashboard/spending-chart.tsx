"use client";

import { useQuery } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { analyticsApi } from "@/services/analytics-api";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export function SpendingChart() {
  const { data, isLoading } = useQuery({
    queryKey: ["spend-trend"],
    queryFn: analyticsApi.spendTrend,
    refetchInterval: 30000,
  });

  return (
    <Card className="p-5">
      <h3 className="text-lg font-semibold text-foreground">Spending overview</h3>
      <p className="text-sm text-slate-400">AI-optimized AP spend vs savings</p>
      <div className="mt-6 h-72">
        {isLoading ? (
          <Skeleton className="h-full w-full" />
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data ?? []}>
              <defs>
                <linearGradient id="spend" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stopColor="#38bdf8" stopOpacity={0.6} />
                  <stop offset="100%" stopColor="#38bdf8" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
              <XAxis dataKey="month" stroke="#94a3b8" />
              <YAxis stroke="#94a3b8" />
              <Tooltip contentStyle={{ background: "#0f172a", border: "1px solid #334155", borderRadius: 8 }} />
              <Area dataKey="spend" stroke="#38bdf8" fill="url(#spend)" />
              <Area dataKey="savings" stroke="#22d3ee" fillOpacity={0} />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>
    </Card>
  );
}

