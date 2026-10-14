import { NotificationBell } from "./notification-bell";

/** Page title block from the mockup: big title, muted subtitle, actions on the right. */
export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-[28px] font-bold leading-tight tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-[15px] text-muted">{subtitle}</p>}
      </div>
      <div className="flex items-center gap-3">
        {actions}
        <div className="hidden lg:block">
          <NotificationBell />
        </div>
      </div>
    </div>
  );
}
