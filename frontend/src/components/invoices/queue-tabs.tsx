"use client";

import { CalendarClock, CopyX, ListChecks, Store } from "lucide-react";

import { useQueueCounts } from "@/lib/api/hooks";
import type { ReviewQueue } from "@/lib/api/types";
import { cn } from "@/lib/utils";

export const QUEUES: { id: ReviewQueue; label: string; hint: string; icon: typeof ListChecks; key: "needsReview" | "possibleDuplicate" | "unverifiedVendor" | "overdue" }[] = [
  { id: "NEEDS_REVIEW", label: "Needs review", hint: "A check failed", icon: ListChecks, key: "needsReview" },
  { id: "POSSIBLE_DUPLICATE", label: "Possible duplicate", hint: "Same vendor and total, dates close", icon: CopyX, key: "possibleDuplicate" },
  { id: "UNVERIFIED_VENDOR", label: "New vendor", hint: "Vendor not confirmed yet", icon: Store, key: "unverifiedVendor" },
  { id: "OVERDUE", label: "Overdue", hint: "Due date passed, still open", icon: CalendarClock, key: "overdue" },
];

/** The review queues with live counts; selecting one filters the invoice list, selecting it again clears it. */
export function QueueTabs({ value, onChange }: { value?: ReviewQueue; onChange: (q: ReviewQueue | undefined) => void }) {
  const { data } = useQueueCounts();
  return (
    <div role="tablist" aria-label="Review queues" className="grid grid-cols-2 gap-2 border-b border-line px-4 py-3 md:grid-cols-4">
      {QUEUES.map(({ id, label, hint, icon: Icon, key }) => {
        const active = value === id;
        const count = data?.[key];
        return (
          <button
            key={id}
            role="tab"
            type="button"
            aria-selected={active}
            title={hint}
            onClick={() => onChange(active ? undefined : id)}
            className={cn(
              "flex items-center gap-2 rounded-control border px-3 py-2 text-left text-sm transition-colors",
              active ? "border-accent/50 bg-accent-soft text-accent" : "border-line text-muted hover:bg-surface hover:text-fg",
            )}
          >
            <Icon className="size-4 shrink-0" aria-hidden />
            <span className="min-w-0 flex-1 truncate">{label}</span>
            <span className={cn("font-mono text-xs", count ? "text-fg" : "text-dim")}>{count ?? "·"}</span>
          </button>
        );
      })}
    </div>
  );
}
