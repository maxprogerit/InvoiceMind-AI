"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const navItems = [
  { href: "/", label: "Dashboard" },
  { href: "/invoices", label: "Invoices" },
  { href: "/receipts", label: "Receipts" },
  { href: "/documents", label: "Documents" },
  { href: "/instructions", label: "Instructions" },
  { href: "/history", label: "History" },
  { href: "/workflows", label: "Workflows" },
  { href: "/approvals", label: "Approvals" },
  { href: "/vendors", label: "Vendors" },
  { href: "/reports", label: "Reports" },
  { href: "/analytics", label: "Analytics" },
  { href: "/executions", label: "Executions" },
  { href: "/settings", label: "Settings" }
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="hidden lg:flex w-72 flex-col border-r border-border/60 bg-black/30 backdrop-blur-xl">
      <div className="px-6 py-8">
        <p className="text-xs uppercase tracking-[0.2em] text-cyan-300/80">InvoiceMind AI</p>
        <h1 className="mt-2 text-2xl font-semibold text-white">Enterprise Automation</h1>
      </div>
      <nav className="space-y-1 px-3 pb-8">
        {navItems.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className={cn(
              "flex rounded-xl px-3 py-2 text-sm transition",
              pathname === item.href
                ? "bg-cyan-500/20 text-cyan-300"
                : "text-slate-300 hover:bg-white/5 hover:text-white"
            )}
          >
            {item.label}
          </Link>
        ))}
      </nav>
    </aside>
  );
}
