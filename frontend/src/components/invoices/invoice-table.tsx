"use client";

import { AlertTriangle, ChevronLeft, ChevronRight, FileSearch } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/misc";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import type { InvoiceSummary, Page } from "@/lib/api/types";
import { formatAmount, formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";

import { StatusBadge } from "./status-badge";

interface Props {
  page: Page<InvoiceSummary> | undefined;
  loading: boolean;
  error?: Error | null;
  onRetry?: () => void;
  selectedId?: string;
  onSelect: (invoice: InvoiceSummary) => void;
  onPage: (page: number) => void;
  emptyHint?: React.ReactNode;
  /** Optional trailing cell (e.g. quick approve/reject buttons on the approvals queue). */
  rowActions?: (invoice: InvoiceSummary) => React.ReactNode;
}

/** The invoices table from the mockup: mono captions, coral highlight on the selected row. */
export function InvoiceTable({ page, loading, error, onRetry, selectedId, onSelect, onPage, emptyHint, rowActions }: Props) {
  if (error) {
    return (
      <div className="flex flex-col items-center gap-3 px-6 py-16 text-center">
        <AlertTriangle className="size-7 text-bad" />
        <p className="text-sm text-muted">Could not load invoices: {error.message}</p>
        {onRetry && (
          <Button variant="outline" size="sm" onClick={onRetry}>
            Try again
          </Button>
        )}
      </div>
    );
  }

  const rows = page?.items ?? [];
  return (
    <div>
      <Table>
        <THead>
          <TR>
            <TH>Vendor</TH>
            <TH>Invoice #</TH>
            <TH>Date</TH>
            <TH className="text-right">Total</TH>
            <TH>Status</TH>
            {rowActions && <TH className="text-right">Actions</TH>}
          </TR>
        </THead>
        <TBody>
          {loading &&
            Array.from({ length: 6 }).map((_, i) => (
              <TR key={i}>
                <TD colSpan={rowActions ? 6 : 5}>
                  <Skeleton className="h-5 w-full" />
                </TD>
              </TR>
            ))}
          {!loading &&
            rows.map((inv) => {
              const selected = inv.id === selectedId;
              return (
                <TR
                  key={inv.id}
                  onClick={() => onSelect(inv)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      onSelect(inv);
                    }
                  }}
                  tabIndex={0}
                  aria-selected={selected}
                  className="cursor-pointer outline-none"
                >
                  <TD className={cn("rounded-l-[10px] border-y border-l border-transparent font-medium", selected && "border-accent bg-surface-2/60")}>
                    <span className="flex items-center gap-2">
                      <span className="max-w-[220px] truncate">{inv.vendorName ?? <span className="text-dim">Unknown vendor</span>}</span>
                      {inv.errorCount > 0 && <AlertTriangle className="size-3.5 shrink-0 text-warn" aria-label={`${inv.errorCount} validation errors`} />}
                    </span>
                  </TD>
                  <TD className={cn("border-y border-transparent font-mono text-[13px] text-muted", selected && "border-accent bg-surface-2/60 text-accent")}>
                    {inv.invoiceNumber ?? "—"}
                  </TD>
                  <TD className={cn("border-y border-transparent font-mono text-[13px] text-muted", selected && "border-accent bg-surface-2/60 text-accent")}>
                    {formatDate(inv.invoiceDate)}
                  </TD>
                  <TD className={cn("num border-y border-transparent text-right font-medium", selected && "border-accent bg-surface-2/60 text-accent")}>
                    {inv.total != null ? (
                      <>
                        {formatAmount(inv.total)} <span className="font-mono text-[10px] text-dim">{inv.currency}</span>
                      </>
                    ) : (
                      "—"
                    )}
                  </TD>
                  <TD className={cn("border-y border-transparent", !rowActions && "rounded-r-[10px] border-r", selected && "border-accent bg-surface-2/60")}>
                    <StatusBadge status={inv.status} />
                  </TD>
                  {rowActions && (
                    <TD
                      className={cn("rounded-r-[10px] border-y border-r border-transparent text-right", selected && "border-accent bg-surface-2/60")}
                      onClick={(e) => e.stopPropagation()}
                    >
                      {rowActions(inv)}
                    </TD>
                  )}
                </TR>
              );
            })}
        </TBody>
      </Table>

      {!loading && rows.length === 0 && (
        <div className="flex flex-col items-center gap-3 px-6 py-16 text-center">
          <div className="grid size-12 place-items-center rounded-full border border-line bg-surface-2 text-dim">
            <FileSearch className="size-5" />
          </div>
          <p className="max-w-xs text-sm text-muted">{emptyHint ?? "No invoices match these filters."}</p>
        </div>
      )}

      {page && page.totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-line px-4 py-3">
          <span className="font-mono text-xs text-dim">
            {page.page * page.size + 1}–{Math.min((page.page + 1) * page.size, page.totalItems)} of {page.totalItems}
          </span>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" disabled={page.page === 0} onClick={() => onPage(page.page - 1)}>
              <ChevronLeft /> Prev
            </Button>
            <Button variant="outline" size="sm" disabled={page.page + 1 >= page.totalPages} onClick={() => onPage(page.page + 1)}>
              Next <ChevronRight />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
