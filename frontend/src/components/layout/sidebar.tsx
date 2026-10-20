"use client";

import { LogOut } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

import { Logo } from "@/components/brand/logo";
import { Badge } from "@/components/ui/badge";
import { authFetch } from "@/lib/api/client";
import { useInvoices } from "@/lib/api/hooks";
import { isReviewer } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/store";
import { initials } from "@/lib/format";
import { useLive } from "@/lib/ws/live-store";
import { cn } from "@/lib/utils";
import { useQueryClient } from "@tanstack/react-query";

import { ClientSwitcher } from "./client-switcher";
import { NAV } from "./nav";

export function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const user = useAuth((s) => s.user);
  const reviewer = isReviewer(user?.role);
  const waiting = useInvoices({ status: ["VALIDATED"], size: 1 }, { enabled: reviewer });

  return (
    <nav aria-label="Main" className="space-y-1">
      {NAV.filter((item) => (!item.roles || (user && item.roles.includes(user.role))) && (!item.firmRoles || (user?.firmRole && item.firmRoles.includes(user.firmRole)))).map(({ href, label, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(`${href}/`);
        return (
          <Link
            key={href}
            href={href}
            onClick={onNavigate}
            aria-current={active ? "page" : undefined}
            className={cn(
              "group flex h-11 items-center gap-3 rounded-control px-3.5 text-[15px] font-medium transition-colors",
              active ? "bg-surface-2 text-accent" : "text-muted hover:bg-surface hover:text-fg",
            )}
          >
            <Icon className={cn("size-[18px]", active ? "text-accent" : "text-dim group-hover:text-muted")} aria-hidden />
            {label}
            {href === "/approvals" && (waiting.data?.totalItems ?? 0) > 0 && (
              <Badge tone="accent" className="ml-auto">
                {waiting.data?.totalItems}
              </Badge>
            )}
          </Link>
        );
      })}
    </nav>
  );
}

function OrgName() {
  const org = useAuth((s) => s.user?.organizationName);
  return org ? <span className="mt-1 block truncate font-mono text-[11px] text-dim">{org}</span> : null;
}

export function UserPanel() {
  const router = useRouter();
  const qc = useQueryClient();
  const user = useAuth((s) => s.user);
  const connected = useLive((s) => s.connected);

  async function signOut() {
    await authFetch("/api/v1/auth/logout", { method: "POST", headers: { "X-Requested-With": "invoicemind" } }).catch(() => undefined);
    useAuth.getState().clear(true);
    qc.clear();
    router.replace("/login");
  }

  if (!user) return null;
  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 font-mono text-[11px] text-dim" title={connected ? "Receiving live updates" : "Live updates disconnected, refreshing periodically"}>
        <span className={cn("size-1.5 rounded-full", connected ? "bg-ok" : "bg-warn animate-pulse-soft")} aria-hidden />
        {connected ? "live" : "reconnecting…"}
      </div>
      <div className="flex items-center gap-3 rounded-control border border-line bg-bg/50 p-2.5">
        <div className="grid size-9 shrink-0 place-items-center rounded-full bg-accent-soft font-mono text-xs font-semibold text-accent">
          {initials(user.fullName)}
        </div>
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium">{user.fullName}</div>
          <div className="truncate font-mono text-[11px] capitalize text-dim">{user.role.toLowerCase()}</div>
        </div>
        <button
          onClick={signOut}
          className="rounded-md p-1.5 text-dim transition-colors hover:bg-surface-2 hover:text-fg"
          aria-label="Sign out"
          title="Sign out"
        >
          <LogOut className="size-4" />
        </button>
      </div>
    </div>
  );
}

export function Sidebar() {
  return (
    <aside className="sticky top-0 hidden h-dvh w-[232px] shrink-0 flex-col border-r border-line px-5 py-7 lg:flex">
      <Link href="/invoices" className="mb-9 block px-1.5" aria-label="InvoiceMind home">
        <Logo />
        <ClientSwitcher fallback={<OrgName />} />
      </Link>
      <div className="flex-1 overflow-y-auto">
        <NavLinks />
      </div>
      <UserPanel />
    </aside>
  );
}
