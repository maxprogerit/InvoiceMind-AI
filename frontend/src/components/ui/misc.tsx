import * as React from "react";

import { cn } from "@/lib/utils";

export function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("skeleton rounded-md", className)} {...props} />;
}

export function Separator({ className, ...props }: React.ComponentProps<"div">) {
  return <div role="separator" className={cn("h-px w-full bg-line", className)} {...props} />;
}

export function Progress({ value, className }: { value: number; className?: string }) {
  const pct = Math.max(0, Math.min(100, value));
  return (
    <div
      role="progressbar"
      aria-valuenow={Math.round(pct)}
      aria-valuemin={0}
      aria-valuemax={100}
      className={cn("h-1.5 w-full overflow-hidden rounded-full bg-surface-3", className)}
    >
      <div className="h-full rounded-full bg-accent transition-[width] duration-200" style={{ width: `${pct}%` }} />
    </div>
  );
}

export function Kbd({ className, ...props }: React.ComponentProps<"kbd">) {
  return <kbd className={cn("rounded border border-line-strong bg-surface-2 px-1.5 py-0.5 font-mono text-[10px] text-muted", className)} {...props} />;
}
