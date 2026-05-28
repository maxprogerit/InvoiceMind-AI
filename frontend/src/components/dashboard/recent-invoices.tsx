"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Card } from "@/components/ui/card";
import { SkeletonRow } from "@/components/ui/skeleton";
import { dashboardApi } from "@/services/dashboard-api";
import { statusBadge } from "@/components/ui/badge";

export function RecentInvoices() {
  const { data, isLoading } = useQuery({
    queryKey: ["dashboard-overview"],
    queryFn: dashboardApi.overview,
    refetchInterval: 15000,
  });

  const invoices = (data?.recentInvoices ?? []) as Array<{
    id: number; invoiceNumber: string; vendorName?: string; status: string;
    amount?: number; currency?: string; confidence?: number;
  }>;

  return (
    <Card className="p-5">
      <h3 className="text-lg font-semibold text-foreground">Recent invoices</h3>
      <div className="mt-4 overflow-x-auto">
        {isLoading ? (
          <div className="space-y-2">{Array.from({ length: 5 }).map((_, i) => <SkeletonRow key={i} />)}</div>
        ) : invoices.length === 0 ? (
          <p className="py-8 text-center text-sm text-slate-500">No invoices yet</p>
        ) : (
          <table className="w-full min-w-[680px] text-sm">
            <thead className="text-slate-400">
              <tr>
                <th className="py-2 text-left">Invoice</th>
                <th className="py-2 text-left">Vendor</th>
                <th className="py-2 text-left">Amount</th>
                <th className="py-2 text-left">Confidence</th>
                <th className="py-2 text-left">Status</th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.id} className="border-t border-border/40 text-foreground/90">
                  <td className="py-3">
                    <Link href="/invoices" className="text-cyan-300 hover:text-cyan-200">
                      {inv.invoiceNumber ?? inv.id}
                    </Link>
                  </td>
                  <td>{inv.vendorName ?? "—"}</td>
                  <td>{inv.currency ?? "USD"} {Number(inv.amount ?? 0).toLocaleString()}</td>
                  <td>{inv.confidence != null ? (Number(inv.confidence) * 100).toFixed(1) + "%" : "—"}</td>
                  <td>{statusBadge(inv.status)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </Card>
  );
}

