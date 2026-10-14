"use client";

import { Download, UploadCloud } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

import { InvoiceFiltersBar } from "@/components/invoices/invoice-filters";
import { InvoicePreviewCard } from "@/components/invoices/invoice-preview-card";
import { InvoiceTable } from "@/components/invoices/invoice-table";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useInvoices, type InvoiceFilters } from "@/lib/api/hooks";
import { useDebounced } from "@/lib/use-debounced";

export default function InvoicesPage() {
  const [filters, setFilters] = useState<InvoiceFilters>({ page: 0, size: 12 });
  const [selectedId, setSelectedId] = useState<string>();
  const debouncedQ = useDebounced(filters.q, 300);
  const query = useInvoices({ ...filters, q: debouncedQ });

  // Like the mockup, something is always selected: default to the first row of the current page.
  const firstId = query.data?.items[0]?.id;
  useEffect(() => {
    if (!selectedId || (query.data && !query.data.items.some((i) => i.id === selectedId))) setSelectedId(firstId);
  }, [firstId, selectedId, query.data]);

  const exportParams = new URLSearchParams();
  filters.status?.forEach((s) => exportParams.append("status", s));
  if (filters.q) exportParams.set("q", filters.q);
  if (filters.from) exportParams.set("from", filters.from);
  if (filters.to) exportParams.set("to", filters.to);

  return (
    <>
      <PageHeader
        title="Invoices"
        subtitle="Scan, review, and export your invoices in one place."
        actions={
          <>
            <Button asChild variant="outline">
              <Link href={`/export?${exportParams.toString()}`}>
                <Download /> Export
              </Link>
            </Button>
            <Button asChild>
              <Link href="/upload">
                <UploadCloud /> Upload invoice
              </Link>
            </Button>
          </>
        }
      />

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.55fr)_minmax(340px,1fr)]">
        <Card className="overflow-hidden">
          <InvoiceFiltersBar value={filters} onChange={setFilters} />
          <InvoiceTable
            page={query.data}
            loading={query.isLoading}
            error={query.error}
            onRetry={() => void query.refetch()}
            selectedId={selectedId}
            onSelect={(i) => setSelectedId(i.id)}
            onPage={(page) => setFilters((f) => ({ ...f, page }))}
            emptyHint={
              filters.q || filters.status?.length || filters.from || filters.to ? (
                "No invoices match these filters."
              ) : (
                <>
                  Nothing here yet.{" "}
                  <Link href="/upload" className="text-accent hover:underline">
                    Upload your first invoice
                  </Link>{" "}
                  and it will appear as soon as it is read.
                </>
              )
            }
          />
        </Card>
        <div className="xl:sticky xl:top-8">
          <InvoicePreviewCard invoiceId={selectedId} />
        </div>
      </div>
    </>
  );
}
