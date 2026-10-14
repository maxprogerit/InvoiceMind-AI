import * as React from "react";

import { cn } from "@/lib/utils";

export function Card({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("rounded-card border border-line bg-surface", className)} {...props} />;
}

export function CardHeader({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("flex items-center justify-between gap-4 px-6 pt-5", className)} {...props} />;
}

export function CardTitle({ className, ...props }: React.ComponentProps<"h3">) {
  return <h3 className={cn("text-lg font-semibold tracking-tight", className)} {...props} />;
}

/** The small mono caption used above card content in the mockups ("INVOICE DETAILS"). */
export function CardLabel({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("label-mono", className)} {...props} />;
}

export function CardContent({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("p-6", className)} {...props} />;
}
