"use client";

import { BarChart3, Table2 } from "lucide-react";
import { useState } from "react";

import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/misc";
import { cn } from "@/lib/utils";

/** Series colours (dark surface). Slots 1-2 of the validated categorical palette; coral is the brand's single-series hue. */
export const CHART = {
  accent: "#ff6b57",
  blue: "#3987e5",
  orange: "#d95926",
  grid: "#2c2122",
  axis: "#75666a",
  ink: "#f5ecec",
  inkMuted: "#a39293",
  surface: "#1a1213",
} as const;

export const axisTick = { fill: CHART.axis, fontSize: 11, fontFamily: "var(--font-mono)" };

export interface TableData {
  columns: string[];
  rows: (string | number)[][];
}

/** Card with title, chart/table toggle (the table is the accessible, exact alternative) and empty/loading states. */
export function ChartCard({
  title,
  subtitle,
  loading,
  empty,
  emptyText = "No data yet. Approve some invoices to see this chart.",
  legend,
  table,
  className,
  children,
}: {
  title: string;
  subtitle?: string;
  loading?: boolean;
  empty?: boolean;
  emptyText?: string;
  legend?: { label: string; color: string }[];
  table?: TableData;
  className?: string;
  children: React.ReactNode;
}) {
  const [view, setView] = useState<"chart" | "table">("chart");
  return (
    <Card className={cn("flex flex-col p-6", className)}>
      <div className="flex items-start justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold tracking-tight">{title}</h3>
          {subtitle && <p className="mt-0.5 text-sm text-muted">{subtitle}</p>}
        </div>
        {table && !empty && !loading && (
          <div className="flex shrink-0 rounded-md border border-line p-0.5" role="group" aria-label="Chart view">
            {(["chart", "table"] as const).map((v) => (
              <button
                key={v}
                onClick={() => setView(v)}
                aria-pressed={view === v}
                aria-label={v === "chart" ? "Show chart" : "Show data table"}
                className={cn("rounded p-1.5 transition-colors", view === v ? "bg-surface-3 text-accent" : "text-dim hover:text-fg")}
              >
                {v === "chart" ? <BarChart3 className="size-3.5" /> : <Table2 className="size-3.5" />}
              </button>
            ))}
          </div>
        )}
      </div>

      {legend && legend.length > 1 && (
        <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-1.5" aria-label="Legend">
          {legend.map((l) => (
            <li key={l.label} className="flex items-center gap-2 text-xs text-muted">
              <span className="size-2.5 rounded-[3px]" style={{ background: l.color }} aria-hidden />
              {l.label}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-5 min-h-[260px] flex-1">
        {loading ? (
          <Skeleton className="h-[260px] w-full" />
        ) : empty ? (
          <div className="grid h-[260px] place-items-center rounded-control border border-dashed border-line-strong px-6 text-center text-sm text-dim">{emptyText}</div>
        ) : view === "table" && table ? (
          <div className="max-h-[300px] overflow-auto">
            <table className="w-full text-sm">
              <thead>
                <tr>
                  {table.columns.map((c) => (
                    <th key={c} className="label-mono border-b border-line py-2 pr-4 text-left font-medium last:text-right">
                      {c}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {table.rows.map((r, i) => (
                  <tr key={i}>
                    {r.map((cell, j) => (
                      <td key={j} className={cn("border-b border-line/60 py-2 pr-4 font-mono text-[13px]", j === r.length - 1 && "num text-right")}>
                        {cell}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          children
        )}
      </div>
    </Card>
  );
}

export function ChartTooltip({
  active,
  label,
  items,
}: {
  active?: boolean;
  label?: string;
  items: { name: string; value: string; color?: string }[];
}) {
  if (!active || items.length === 0) return null;
  return (
    <div className="rounded-control border border-line-strong bg-surface-3 px-3 py-2.5 shadow-xl">
      {label && <div className="mb-1.5 font-mono text-[11px] text-dim">{label}</div>}
      {items.map((it) => (
        <div key={it.name} className="flex items-center justify-between gap-6 text-sm">
          <span className="flex items-center gap-2 text-muted">
            {it.color && <span className="size-2 rounded-[2px]" style={{ background: it.color }} aria-hidden />}
            {it.name}
          </span>
          <span className="num font-medium text-fg">{it.value}</span>
        </div>
      ))}
    </div>
  );
}
