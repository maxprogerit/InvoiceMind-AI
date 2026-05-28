import { Card } from "@/components/ui/card";

interface PageShellProps {
  title: string;
  subtitle: string;
  children: React.ReactNode;
}

export function PageShell({ title, subtitle, children }: PageShellProps) {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-semibold text-foreground">{title}</h2>
        <p className="mt-1 text-slate-400">{subtitle}</p>
      </div>
      <Card className="p-5">{children}</Card>
    </div>
  );
}
