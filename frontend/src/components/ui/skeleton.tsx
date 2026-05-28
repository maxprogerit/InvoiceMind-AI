import { cn } from "@/lib/utils";

interface SkeletonProps {
  className?: string;
}

export function Skeleton({ className }: SkeletonProps) {
  return (
    <div className={cn("animate-pulse rounded-lg bg-slate-800/60", className)} />
  );
}

export function SkeletonRow() {
  return (
    <div className="flex items-center gap-4 rounded-xl border border-border/40 p-4">
      <Skeleton className="h-9 w-9 rounded-full" />
      <div className="flex-1 space-y-2">
        <Skeleton className="h-3.5 w-1/3" />
        <Skeleton className="h-3 w-1/2" />
      </div>
      <Skeleton className="h-6 w-20 rounded-full" />
    </div>
  );
}

export function SkeletonCard() {
  return (
    <div className="space-y-3 rounded-xl border border-border/60 p-5">
      <Skeleton className="h-3 w-1/4" />
      <Skeleton className="h-8 w-1/2" />
      <Skeleton className="h-3 w-1/3" />
    </div>
  );
}
