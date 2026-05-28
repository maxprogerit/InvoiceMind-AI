import { cn } from "@/lib/utils";

export function Button({ className, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      className={cn(
        "inline-flex h-10 items-center justify-center rounded-xl border border-cyan-400/40 bg-cyan-500/15 px-4 text-sm font-medium text-cyan-300 transition hover:bg-cyan-400/20",
        className
      )}
      {...props}
    />
  );
}
