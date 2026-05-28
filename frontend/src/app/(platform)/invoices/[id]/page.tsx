"use client";

import { useQuery } from "@tanstack/react-query";
import { use } from "react";
import { PageShell } from "@/components/platform/page-shell";
import { Skeleton } from "@/components/ui/skeleton";
import { statusBadge } from "@/components/ui/badge";
import { invoicesApi } from "@/services/invoices-api";

export default function InvoiceDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);

  const { data: inv, isLoading } = useQuery({
    queryKey: ["invoice", id],
    queryFn: () => invoicesApi.get(Number(id)),
  });

  if (isLoading) return (
    <PageShell title="Invoice" subtitle="Loading…">
      <Skeleton className="h-48 w-full" />
    </PageShell>
  );
  if (!inv) return <PageShell title="Invoice Not Found" subtitle="" />;

  return (
    <PageShell title={`Invoice ${inv.invoiceNumber}`} subtitle="OCR extraction results, AI recommendations, and approval status.">
      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2 rounded-xl border border-border/60 p-4">
          <p className="text-xs text-slate-400">Vendor</p>
          <p className="text-lg font-semibold">{inv.vendorName}</p>
          <p className="text-xs text-slate-400">Amount</p>
          <p>{inv.currency} {Number(inv.totalAmount).toLocaleString()}</p>
          <p className="text-xs text-slate-400">Due Date</p>
          <p>{inv.dueDate ?? "—"}</p>
        </div>
        <div className="space-y-2 rounded-xl border border-border/60 p-4">
          <p className="text-xs text-slate-400">Status</p>
          {statusBadge(inv.status)}
          <p className="text-xs text-slate-400 mt-3">AI confidence</p>
          <p className="text-lg font-semibold text-cyan-300">
            {inv.confidenceScore != null ? (Number(inv.confidenceScore) * 100).toFixed(1) + "%" : "—"}
          </p>
          <p className="text-xs text-slate-400">Smart category</p>
          <p>{inv.smartCategory ?? "—"}</p>
        </div>
      </div>
    </PageShell>
  );
}

