import { cn } from "@/lib/utils";

export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn("text-[22px] font-bold tracking-tight", className)}>
      Invoice<span className="text-accent">Mind</span>
    </span>
  );
}

export function Splash() {
  return (
    <div className="grid min-h-dvh place-items-center bg-bg" role="status" aria-label="Loading">
      <Logo className="animate-pulse-soft text-3xl" />
    </div>
  );
}
