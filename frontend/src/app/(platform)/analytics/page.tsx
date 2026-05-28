"use client";

import { useQuery } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { Skeleton } from "@/components/ui/skeleton";
import { analyticsApi } from "@/services/analytics-api";
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid,
  ResponsiveContainer, Tooltip, XAxis, YAxis, Cell
} from "recharts";

const COLORS = ["#38bdf8", "#22d3ee", "#a78bfa", "#34d399", "#fb923c"];

export default function AnalyticsPage() {
  const { data: summary } = useQuery({ queryKey: ["analytics-summary"], queryFn: analyticsApi.summary });
  const { data: spend = [], isLoading: loadSpend } = useQuery({ queryKey: ["spend-trend"], queryFn: analyticsApi.spendTrend });
  const { data: volume = [], isLoading: loadVol } = useQuery({ queryKey: ["doc-volume"], queryFn: analyticsApi.documentVolume });
  const { data: vendorSpend = [], isLoading: loadVendor } = useQuery({ queryKey: ["vendor-spend"], queryFn: analyticsApi.vendorSpend });

  return (
    <PageShell title="Analytics" subtitle="AI accuracy, workflow efficiency, cost reduction, and forecasting insights.">
      {/* Summary cards */}
      {summary && (
        <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[
            { label: "Total documents", value: summary.totalDocuments },
            { label: "OCR Accuracy", value: `${summary.ocrAccuracy?.toFixed(1) ?? 0}%` },
            { label: "Success rate", value: `${summary.successRate?.toFixed(1) ?? 0}%` },
            { label: "Cost reduction", value: `${summary.costReductionPercent?.toFixed(1) ?? 0}%` },
          ].map((s) => (
            <div key={s.label} className="rounded-xl border border-border/40 p-4">
              <p className="text-xs text-slate-500">{s.label}</p>
              <p className="mt-1 text-2xl font-bold text-foreground">{s.value}</p>
            </div>
          ))}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Spend trend */}
        <div className="rounded-xl border border-border/40 p-4">
          <h3 className="mb-4 font-semibold">Monthly Spend</h3>
          {loadSpend ? <Skeleton className="h-56 w-full" /> : (
            <ResponsiveContainer width="100%" height={220}>
              <AreaChart data={spend}>
                <defs>
                  <linearGradient id="spendGrad" x1="0" x2="0" y1="0" y2="1">
                    <stop offset="0%" stopColor="#38bdf8" stopOpacity={0.5} />
                    <stop offset="100%" stopColor="#38bdf8" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis dataKey="month" stroke="#94a3b8" tick={{ fontSize: 11 }} />
                <YAxis stroke="#94a3b8" tick={{ fontSize: 11 }} />
                <Tooltip contentStyle={{ background: "#0f172a", border: "1px solid #334155", borderRadius: 8 }} />
                <Area dataKey="spend" stroke="#38bdf8" fill="url(#spendGrad)" />
              </AreaChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Document volume */}
        <div className="rounded-xl border border-border/40 p-4">
          <h3 className="mb-4 font-semibold">Document Volume</h3>
          {loadVol ? <Skeleton className="h-56 w-full" /> : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={volume}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis dataKey="month" stroke="#94a3b8" tick={{ fontSize: 11 }} />
                <YAxis stroke="#94a3b8" tick={{ fontSize: 11 }} />
                <Tooltip contentStyle={{ background: "#0f172a", border: "1px solid #334155", borderRadius: 8 }} />
                <Bar dataKey="invoices" fill="#a78bfa" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Vendor spend */}
        <div className="rounded-xl border border-border/40 p-4 lg:col-span-2">
          <h3 className="mb-4 font-semibold">Vendor Spend Breakdown</h3>
          {loadVendor ? <Skeleton className="h-56 w-full" /> : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={vendorSpend} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                <XAxis type="number" stroke="#94a3b8" tick={{ fontSize: 11 }} />
                <YAxis type="category" dataKey="vendor" stroke="#94a3b8" tick={{ fontSize: 11 }} width={100} />
                <Tooltip contentStyle={{ background: "#0f172a", border: "1px solid #334155", borderRadius: 8 }} />
                <Bar dataKey="spend" radius={[0, 4, 4, 0]}>
                  {vendorSpend.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>
    </PageShell>
  );
}

