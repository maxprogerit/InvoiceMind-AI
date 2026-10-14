"use client";

import { Search, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ALL_STATUSES, type InvoiceStatus } from "@/lib/api/types";
import type { InvoiceFilters } from "@/lib/api/hooks";
import { STATUS_META } from "@/lib/status";
import { cn } from "@/lib/utils";

const TONE_ACTIVE: Record<string, string> = {
  neutral: "border-line-strong bg-surface-3 text-fg",
  info: "border-info/50 bg-info/15 text-info",
  violet: "border-violet/50 bg-violet/15 text-violet",
  warn: "border-warn/50 bg-warn/15 text-warn",
  ok: "border-ok/50 bg-ok/15 text-ok",
  bad: "border-bad/50 bg-bad/15 text-bad",
  accent: "border-accent/50 bg-accent-soft text-accent",
};

/** Search + status pills + date range. State is owned by the page so it can live in the URL. */
export function InvoiceFiltersBar({
  value,
  onChange,
  statuses = ALL_STATUSES,
}: {
  value: InvoiceFilters;
  onChange: (next: InvoiceFilters) => void;
  statuses?: InvoiceStatus[];
}) {
  const selected = value.status ?? [];
  const toggle = (s: InvoiceStatus) =>
    onChange({ ...value, page: 0, status: selected.includes(s) ? selected.filter((x) => x !== s) : [...selected, s] });
  const dirty = !!(value.q || value.from || value.to || selected.length);

  return (
    <div className="space-y-3 border-b border-line px-4 py-4">
      <div className="flex flex-wrap gap-3">
        <div className="relative min-w-52 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-dim" aria-hidden />
          <Input
            aria-label="Search invoices"
            placeholder="Search vendor, number or file…"
            className="pl-9"
            value={value.q ?? ""}
            onChange={(e) => onChange({ ...value, page: 0, q: e.target.value })}
          />
        </div>
        <Input
          type="date"
          aria-label="From date"
          className="w-40 font-mono text-xs"
          value={value.from ?? ""}
          onChange={(e) => onChange({ ...value, page: 0, from: e.target.value || undefined })}
        />
        <Input
          type="date"
          aria-label="To date"
          className="w-40 font-mono text-xs"
          value={value.to ?? ""}
          onChange={(e) => onChange({ ...value, page: 0, to: e.target.value || undefined })}
        />
        {dirty && (
          <Button variant="ghost" size="sm" className="self-center" onClick={() => onChange({ page: 0, size: value.size, sort: value.sort })}>
            <X /> Clear
          </Button>
        )}
      </div>
      <div className="flex flex-wrap gap-2" role="group" aria-label="Filter by status">
        {statuses.map((s) => {
          const on = selected.includes(s);
          return (
            <button
              key={s}
              type="button"
              aria-pressed={on}
              onClick={() => toggle(s)}
              className={cn(
                "rounded-md border px-2.5 py-1 font-mono text-[11px] transition-colors",
                on ? TONE_ACTIVE[STATUS_META[s].tone] : "border-line text-dim hover:border-line-strong hover:text-muted",
              )}
            >
              {STATUS_META[s].label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
