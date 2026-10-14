"use client";

import { format, parseISO } from "date-fns";
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import type { ByStatus, SpendByMonth, SpendByVendor, VolumeByDay } from "@/lib/api/types";
import { formatAmount, formatCompactMoney, formatMoney } from "@/lib/format";
import { STATUS_META } from "@/lib/status";
import { cn } from "@/lib/utils";

import { axisTick, ChartCard, ChartTooltip, CHART } from "./chart-card";

const monthLabel = (m: string) => format(parseISO(`${m}-01`), "MMM yy");

/** Marker only on days that actually have data, so a sparse series is not mistaken for a flat line. */
function ActiveDot({ cx, cy, payload, dataKey, color }: { cx?: number; cy?: number; payload?: Record<string, number>; dataKey: string; color: string }) {
  if (cx === undefined || cy === undefined || !payload || !(payload[dataKey]! > 0)) return <g />;
  return <circle cx={cx} cy={cy} r={4} fill={color} stroke={CHART.surface} strokeWidth={2} />;
}

export function SpendByMonthChart({ data, loading }: { data?: SpendByMonth; loading: boolean }) {
  const items = data?.items ?? [];
  const cur = data?.currency ?? "";
  return (
    <ChartCard
      title="Approved spend by month"
      subtitle={cur ? `Approved invoices in ${cur}, by invoice date` : undefined}
      loading={loading}
      empty={!items.some((i) => i.invoices > 0)}
      table={{ columns: ["Month", "Invoices", `Amount (${cur})`], rows: items.map((i) => [i.month, i.invoices, formatAmount(i.amount)]) }}
    >
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={items} margin={{ top: 8, right: 8, left: 0, bottom: 0 }} barCategoryGap="28%">
          <CartesianGrid vertical={false} stroke={CHART.grid} />
          <XAxis dataKey="month" tickFormatter={monthLabel} tick={axisTick} axisLine={false} tickLine={false} interval="preserveStartEnd" />
          <YAxis tick={axisTick} axisLine={false} tickLine={false} width={56} tickFormatter={(v: number) => formatCompactMoney(v, cur || "USD")} />
          <Tooltip
            cursor={{ fill: "rgb(255 255 255 / 0.04)" }}
            content={({ active, payload, label }) => {
              const p = payload?.[0]?.payload as (typeof items)[number] | undefined;
              return (
                <ChartTooltip
                  active={active}
                  label={label ? monthLabel(String(label)) : undefined}
                  items={p ? [{ name: "Approved", value: formatMoney(p.amount, cur), color: CHART.accent }, { name: "Invoices", value: String(p.invoices) }] : []}
                />
              );
            }}
          />
          <Bar dataKey="amount" fill={CHART.accent} radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}

export function SpendByVendorChart({ data, loading }: { data?: SpendByVendor; loading: boolean }) {
  const items = data?.items ?? [];
  const cur = data?.currency ?? "";
  const max = Math.max(1, ...items.map((i) => Number(i.amount)));
  return (
    <ChartCard
      title="Top vendors"
      subtitle={cur ? `Approved spend in ${cur}` : undefined}
      loading={loading}
      empty={items.length === 0}
      table={{ columns: ["Vendor", "Invoices", `Amount (${cur})`], rows: items.map((i) => [i.vendor, i.invoices, formatAmount(i.amount)]) }}
    >
      <ul className="space-y-3.5" aria-label="Spend by vendor">
        {items.map((v) => (
          <li key={v.vendor}>
            <div className="mb-1.5 flex items-baseline justify-between gap-4">
              <span className="truncate text-sm font-medium" title={v.vendor}>
                {v.vendor}
              </span>
              <span className="num shrink-0 font-mono text-[13px] text-muted">
                {formatMoney(v.amount, cur)} <span className="text-dim">· {v.invoices}</span>
              </span>
            </div>
            <div className="h-2 rounded-full bg-surface-3">
              <div className="h-full rounded-full bg-accent transition-[width] duration-500" style={{ width: `${Math.max(2, (Number(v.amount) / max) * 100)}%` }} />
            </div>
          </li>
        ))}
      </ul>
    </ChartCard>
  );
}

export function VolumeChart({ data, loading }: { data?: VolumeByDay; loading: boolean }) {
  const items = data?.items ?? [];
  const label = (d: string) => format(parseISO(d), "d MMM");
  return (
    <ChartCard
      title="Upload volume"
      subtitle="Invoices uploaded per day, last 30 days"
      loading={loading}
      empty={!items.some((i) => i.uploaded > 0)}
      emptyText="No uploads in the last 30 days."
      legend={[
        { label: "Uploaded", color: CHART.blue },
        { label: "Needed review", color: CHART.orange },
      ]}
      table={{ columns: ["Day", "Uploaded", "Needed review", "Failed"], rows: items.map((i) => [i.day, i.uploaded, i.flagged, i.failed]) }}
    >
      <ResponsiveContainer width="100%" height={260}>
        <LineChart data={items} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART.grid} />
          <XAxis dataKey="day" tickFormatter={label} tick={axisTick} axisLine={false} tickLine={false} interval="preserveStartEnd" minTickGap={40} />
          <YAxis tick={axisTick} axisLine={false} tickLine={false} width={32} allowDecimals={false} />
          <Tooltip
            cursor={{ stroke: CHART.axis, strokeDasharray: "3 3" }}
            content={({ active, payload, label: l }) => {
              const p = payload?.[0]?.payload as (typeof items)[number] | undefined;
              return (
                <ChartTooltip
                  active={active}
                  label={l ? label(String(l)) : undefined}
                  items={
                    p
                      ? [
                          { name: "Uploaded", value: String(p.uploaded), color: CHART.blue },
                          { name: "Needed review", value: String(p.flagged), color: CHART.orange },
                          { name: "Failed", value: String(p.failed) },
                        ]
                      : []
                  }
                />
              );
            }}
          />
          <Line type="monotone" dataKey="uploaded" stroke={CHART.blue} strokeWidth={2} isAnimationActive={false} dot={<ActiveDot color={CHART.blue} dataKey="uploaded" />} activeDot={{ r: 4, stroke: CHART.surface, strokeWidth: 2 }} />
          <Line type="monotone" dataKey="flagged" stroke={CHART.orange} strokeWidth={2} isAnimationActive={false} dot={<ActiveDot color={CHART.orange} dataKey="flagged" />} activeDot={{ r: 4, stroke: CHART.surface, strokeWidth: 2 }} />
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}

const STATUS_BAR: Record<string, string> = {
  neutral: "bg-dim",
  info: "bg-info",
  violet: "bg-violet",
  warn: "bg-warn",
  ok: "bg-ok",
  bad: "bg-bad",
  accent: "bg-accent",
};

/** Status breakdown as labelled bars: the status palette always travels with a text label. */
export function StatusBreakdown({ data, loading }: { data?: ByStatus; loading: boolean }) {
  const items = data?.items ?? [];
  const total = items.reduce((a, b) => a + b.count, 0);
  return (
    <ChartCard
      title="Invoices by status"
      subtitle={total ? `${total} invoices in total` : undefined}
      loading={loading}
      empty={items.length === 0}
      emptyText="No invoices yet."
      table={{ columns: ["Status", "Share", "Invoices"], rows: items.map((i) => [i.status, `${Math.round((i.count / total) * 100)}%`, i.count]) }}
    >
      <ul className="space-y-3.5" aria-label="Invoices by status">
        {items.map((s) => {
          const meta = STATUS_META[s.status as keyof typeof STATUS_META];
          return (
            <li key={s.status}>
              <div className="mb-1.5 flex items-baseline justify-between">
                <span className="text-sm font-medium">{meta?.label ?? s.status}</span>
                <span className="num font-mono text-[13px] text-muted">
                  {s.count} <span className="text-dim">· {Math.round((s.count / total) * 100)}%</span>
                </span>
              </div>
              <div className="h-2 rounded-full bg-surface-3">
                <div className={cn("h-full rounded-full transition-[width] duration-500", STATUS_BAR[meta?.tone ?? "neutral"])} style={{ width: `${Math.max(2, (s.count / total) * 100)}%` }} />
              </div>
            </li>
          );
        })}
      </ul>
    </ChartCard>
  );
}
