import * as React from "react";

import { cn } from "@/lib/utils";

const field =
  "w-full rounded-control border border-line bg-bg/60 px-3 text-sm text-fg placeholder:text-dim transition-colors hover:border-line-strong focus-visible:border-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/30 disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-bad/70 aria-[invalid=true]:ring-bad/20";

export function Input({ className, type = "text", ...props }: React.ComponentProps<"input">) {
  return <input type={type} className={cn(field, "h-10", className)} {...props} />;
}

export function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return <textarea className={cn(field, "min-h-[88px] resize-y py-2.5", className)} {...props} />;
}

export function Label({ className, ...props }: React.ComponentProps<"label">) {
  return <label className={cn("label-mono mb-1.5 block", className)} {...props} />;
}

export { field as fieldClass };
