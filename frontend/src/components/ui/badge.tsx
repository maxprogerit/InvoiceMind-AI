import { cn } from "@/lib/utils";

type Variant = "default" | "success" | "error" | "warning" | "info" | "processing";

const styles: Record<Variant, string> = {
  default: "bg-slate-700/50 text-slate-300 border-slate-600/40",
  success: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30",
  error: "bg-rose-500/15 text-rose-300 border-rose-500/30",
  warning: "bg-amber-500/15 text-amber-300 border-amber-500/30",
  info: "bg-cyan-500/15 text-cyan-300 border-cyan-500/30",
  processing: "bg-blue-500/15 text-blue-300 border-blue-500/30",
};

export function Badge({ children, variant = "default", className }: {
  children: React.ReactNode;
  variant?: Variant;
  className?: string;
}) {
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium", styles[variant], className)}>
      {children}
    </span>
  );
}

export function statusBadge(status: string) {
  const map: Record<string, Variant> = {
    completed: "success",
    approved: "success",
    paid: "success",
    processed: "success",
    active: "success",
    failed: "error",
    rejected: "error",
    error: "error",
    pending: "warning",
    processing: "processing",
    running: "processing",
    queued: "info",
    uploaded: "info",
    draft: "default",
    inactive: "default",
    cancelled: "default",
    unpaid: "warning",
  };
  return <Badge variant={map[status?.toLowerCase()] ?? "default"}>{status}</Badge>;
}
