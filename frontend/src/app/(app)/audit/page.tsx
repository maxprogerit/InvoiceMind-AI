"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { ACTION_LABEL, DetailLines } from "@/components/invoices/audit-timeline";
import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/misc";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useAudit, type AuditFilters } from "@/lib/api/hooks";
import { isReviewer } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/store";
import { formatDateTime } from "@/lib/format";

const ALL = "__all";

export default function AuditPage() {
  const router = useRouter();
  const user = useAuth((s) => s.user);
  const [filters, setFilters] = useState<AuditFilters>({ page: 0, size: 25 });
  const reviewer = isReviewer(user?.role);
  useEffect(() => {
    if (user && !reviewer) router.replace("/invoices");
  }, [user, reviewer, router]);
  const { data, isLoading, error } = useAudit(filters);
  if (!reviewer) return null;

  return (
    <>
      <PageHeader title="Audit log" subtitle="Every upload, correction, decision and export in your organisation, with who and when." />
      <Card className="overflow-hidden">
        <div className="flex flex-wrap gap-3 border-b border-line p-4">
          <Select value={filters.action ?? ALL} onValueChange={(v) => setFilters((f) => ({ ...f, page: 0, action: v === ALL ? undefined : v }))}>
            <SelectTrigger className="w-60" aria-label="Action">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>All actions</SelectItem>
              {Object.entries(ACTION_LABEL).map(([k, label]) => (
                <SelectItem key={k} value={k}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input type="date" aria-label="From date" className="w-40 font-mono text-xs" value={filters.from ?? ""} onChange={(e) => setFilters((f) => ({ ...f, page: 0, from: e.target.value || undefined }))} />
          <Input type="date" aria-label="To date" className="w-40 font-mono text-xs" value={filters.to ?? ""} onChange={(e) => setFilters((f) => ({ ...f, page: 0, to: e.target.value || undefined }))} />
        </div>

        {error ? (
          <p className="p-10 text-center text-sm text-bad">Could not load the audit log: {error.message}</p>
        ) : (
          <Table>
            <THead>
              <TR>
                <TH>When</TH>
                <TH>Who</TH>
                <TH>Action</TH>
                <TH>Details</TH>
              </TR>
            </THead>
            <TBody>
              {isLoading && Array.from({ length: 8 }).map((_, i) => (
                <TR key={i}>
                  <TD colSpan={4}>
                    <Skeleton className="h-5" />
                  </TD>
                </TR>
              ))}
              {data?.items.map((e) => (
                <TR key={e.id}>
                  <TD className="whitespace-nowrap font-mono text-xs text-muted">{formatDateTime(e.createdAt)}</TD>
                  <TD className="whitespace-nowrap text-sm">{e.userName}</TD>
                  <TD>
                    <Badge tone={e.action.includes("REJECT") || e.action.includes("FAIL") || e.action.includes("DELETE") ? "bad" : e.action.includes("APPROVE") ? "ok" : "neutral"}>
                      {ACTION_LABEL[e.action] ?? e.action}
                    </Badge>
                  </TD>
                  <TD className="py-2 align-top">
                    <DetailLines details={e.details} />
                    {e.entityId && e.entityType === "INVOICE" && (
                      <a href={`/invoices/${e.entityId}`} className="font-mono text-[11px] text-accent hover:underline">
                        open invoice →
                      </a>
                    )}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}

        {data && data.items.length === 0 && <p className="p-10 text-center text-sm text-dim">No entries for these filters.</p>}
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-line px-4 py-3">
            <span className="font-mono text-xs text-dim">
              Page {data.page + 1} of {data.totalPages} · {data.totalItems} entries
            </span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" disabled={data.page === 0} onClick={() => setFilters((f) => ({ ...f, page: data.page - 1 }))}>
                <ChevronLeft /> Prev
              </Button>
              <Button variant="outline" size="sm" disabled={data.page + 1 >= data.totalPages} onClick={() => setFilters((f) => ({ ...f, page: data.page + 1 }))}>
                Next <ChevronRight />
              </Button>
            </div>
          </div>
        )}
      </Card>
    </>
  );
}
