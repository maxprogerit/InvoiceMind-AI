import { FileSearch } from "lucide-react";

interface EmptyStateProps {
  title?: string;
  description?: string;
  action?: React.ReactNode;
}

export function EmptyState({ title = "No data found", description = "Nothing to show here yet.", action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border/60 py-16 text-center">
      <FileSearch className="mb-4 h-10 w-10 text-slate-600" />
      <p className="text-sm font-medium text-slate-400">{title}</p>
      <p className="mt-1 text-xs text-slate-600">{description}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
