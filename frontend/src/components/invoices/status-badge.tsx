"use client";

import { Badge } from "@/components/ui/badge";
import type { InvoiceStatus } from "@/lib/api/types";
import { STATUS_META } from "@/lib/status";
import { cn } from "@/lib/utils";

export function StatusBadge({ status, className }: { status: InvoiceStatus; className?: string }) {
  const meta = STATUS_META[status];
  const working = status === "PROCESSING" || status === "UPLOADED" || status === "EXTRACTED";
  return (
    <Badge tone={meta.tone} className={className} title={meta.hint}>
      <span className={cn("size-1.5 rounded-full bg-current", working && "animate-pulse-soft")} aria-hidden />
      {meta.label}
    </Badge>
  );
}
