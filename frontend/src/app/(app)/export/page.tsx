"use client";

import { Braces, FileSpreadsheet, FileText, Loader2 } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { toast } from "sonner";

import { InvoiceFiltersBar } from "@/components/invoices/invoice-filters";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardLabel } from "@/components/ui/card";
import { Input, Label } from "@/components/ui/input";
import { ApiError, downloadBlob, fetchBlob } from "@/lib/api/client";
import { useInvoices, type InvoiceFilters } from "@/lib/api/hooks";
import { ALL_STATUSES, type InvoiceStatus } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const FORMATS = [
  { id: "xlsx", label: "Excel", ext: ".xlsx", icon: FileSpreadsheet, text: "Two sheets: invoices and line items. Numbers and dates are real cell types." },
  { id: "csv", label: "CSV", ext: ".csv", icon: FileText, text: "UTF-8 with BOM so Excel opens umlauts correctly. One row per invoice." },
  { id: "json", label: "JSON", ext: ".json", icon: Braces, text: "Structured, with nested line items, for scripts and integrations." },
] as const;

function initialFilters(params: URLSearchParams): InvoiceFilters {
  const status = params.getAll("status").filter((s): s is InvoiceStatus => (ALL_STATUSES as string[]).includes(s));
  return { status, q: params.get("q") ?? undefined, from: params.get("from") ?? undefined, to: params.get("to") ?? undefined };
}

function ExportBody() {
  const params = useSearchParams();
  const [filters, setFilters] = useState<InvoiceFilters>(() => initialFilters(params));
  const [minTotal, setMinTotal] = useState("");
  const [maxTotal, setMaxTotal] = useState("");
  const [busy, setBusy] = useState<string | null>(null);

  const active: InvoiceFilters = {
    ...filters,
    minTotal: minTotal ? Number(minTotal) : undefined,
    maxTotal: maxTotal ? Number(maxTotal) : undefined,
  };
  const count = useInvoices({ ...active, size: 1, page: 0 });

  async function run(format: string, ext: string) {
    setBusy(format);
    try {
      const qs = new URLSearchParams({ format });
      active.status?.forEach((s) => qs.append("status", s));
      for (const k of ["q", "from", "to", "minTotal", "maxTotal"] as const) if (active[k] !== undefined && active[k] !== "") qs.set(k, String(active[k]));
      const { blob, filename, rows } = await fetchBlob(`/api/v1/export/invoices?${qs}`);
      downloadBlob(blob, filename ?? `invoices${ext}`);
      toast.success(`Exported ${rows ?? count.data?.totalItems ?? ""} invoices`);
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : "Export failed");
    } finally {
      setBusy(null);
    }
  }

  const total = count.data?.totalItems;
  return (
    <>
      <PageHeader title="Export" subtitle="Download exactly the invoices you filter below." />

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.3fr)_minmax(320px,1fr)]">
        <Card className="overflow-hidden">
          <div className="px-6 pt-5">
            <CardLabel>Filters</CardLabel>
          </div>
          <InvoiceFiltersBar value={filters} onChange={setFilters} />
          <div className="grid gap-4 p-6 sm:grid-cols-2">
            <div>
              <Label htmlFor="min">Total from</Label>
              <Input id="min" inputMode="decimal" placeholder="0.00" className="font-mono" value={minTotal} onChange={(e) => setMinTotal(e.target.value)} />
            </div>
            <div>
              <Label htmlFor="max">Total up to</Label>
              <Input id="max" inputMode="decimal" placeholder="no limit" className="font-mono" value={maxTotal} onChange={(e) => setMaxTotal(e.target.value)} />
            </div>
          </div>
          <div className="border-t border-line px-6 py-4 text-sm text-muted" aria-live="polite">
            {count.isLoading ? "Counting…" : total === 0 ? "No invoices match. Adjust the filters." : `${total} ${total === 1 ? "invoice" : "invoices"} will be exported (max 10,000).`}
          </div>
        </Card>

        <div className="space-y-4">
          {FORMATS.map(({ id, label, ext, icon: Icon, text }) => (
            <Card key={id} className={cn("flex items-center gap-4 p-5", busy === id && "border-accent/60")}>
              <div className="grid size-12 shrink-0 place-items-center rounded-control bg-surface-2 text-accent">
                <Icon className="size-6" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-2">
                  <span className="font-semibold">{label}</span>
                  <span className="font-mono text-xs text-dim">{ext}</span>
                </div>
                <p className="mt-0.5 text-[13px] leading-snug text-muted">{text}</p>
              </div>
              <Button variant={id === "xlsx" ? "default" : "outline"} disabled={!!busy || total === 0} onClick={() => run(id, ext)}>
                {busy === id ? <Loader2 className="animate-spin" /> : null} Download
              </Button>
            </Card>
          ))}
        </div>
      </div>
    </>
  );
}

export default function ExportPage() {
  return (
    <Suspense>
      <ExportBody />
    </Suspense>
  );
}
