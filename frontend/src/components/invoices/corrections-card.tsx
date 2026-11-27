"use client";

import { Card, CardLabel } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/misc";
import { useCorrections } from "@/lib/api/hooks";
import { formatDateTime } from "@/lib/format";

const LABELS: Record<string, string> = {
  documentType: "Type",
  vendorName: "Vendor",
  vendorTaxId: "Vendor tax id",
  invoiceNumber: "Number",
  invoiceDate: "Invoice date",
  dueDate: "Due date",
  currency: "Currency",
  subtotal: "Subtotal",
  tax: "Tax",
  total: "Total",
  expenseCategory: "Category",
  lineItems: "Line items",
};

/** Every field a person changed: before, after, who and when. Vendor memory learns from these. */
export function CorrectionsCard({ invoiceId }: { invoiceId: string }) {
  const { data, isLoading } = useCorrections(invoiceId);
  if (isLoading) return <Skeleton className="h-24" />;
  if (!data?.length) return null;
  return (
    <Card className="p-6">
      <CardLabel className="mb-3">Corrections</CardLabel>
      <ul className="divide-y divide-line/60 text-sm">
        {data.map((c) => (
          <li key={c.id} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-2">
            <span className="text-muted">{LABELS[c.field] ?? c.field}</span>
            <span className="font-mono text-[13px]">
              <span className="text-dim line-through">{c.oldValue ?? "empty"}</span> <span aria-hidden>→</span>{" "}
              <span>{c.newValue ?? "empty"}</span>
            </span>
            <span className="w-full text-right font-mono text-[11px] text-dim">
              {c.userName ?? "Unknown"} · {formatDateTime(c.at)}
            </span>
          </li>
        ))}
      </ul>
    </Card>
  );
}
