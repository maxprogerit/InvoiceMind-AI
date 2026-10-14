"use client";

import { CheckCircle2, ShieldCheck, XCircle } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { DecisionDialog } from "@/components/invoices/decision-dialog";
import { InvoiceTable } from "@/components/invoices/invoice-table";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useInvoices } from "@/lib/api/hooks";
import { isReviewer, type InvoiceSummary } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/store";
import { formatMoney } from "@/lib/format";

/** The approver's queue: validated invoices ready to sign off, plus the ones still waiting for a human correction. */
export default function ApprovalsPage() {
  const router = useRouter();
  const user = useAuth((s) => s.user);
  const [tab, setTab] = useState<"VALIDATED" | "NEEDS_REVIEW">("VALIDATED");
  const [page, setPage] = useState(0);
  const [decision, setDecision] = useState<{ inv: InvoiceSummary; kind: "approve" | "reject" } | null>(null);

  const reviewer = isReviewer(user?.role);
  useEffect(() => {
    if (user && !reviewer) router.replace("/invoices");
  }, [user, reviewer, router]);

  const ready = useInvoices({ status: ["VALIDATED"], size: 1 }, { enabled: reviewer });
  const attention = useInvoices({ status: ["NEEDS_REVIEW"], size: 1 }, { enabled: reviewer });
  const list = useInvoices({ status: [tab], page, size: 12, sort: "createdAt,asc" }, { enabled: reviewer });
  if (!reviewer) return null;

  return (
    <>
      <PageHeader title="Approvals" subtitle="Sign off invoices that passed validation. Oldest first." />

      <Card className="overflow-hidden">
        <div className="border-b border-line px-4 py-4">
          <Tabs
            value={tab}
            onValueChange={(v) => {
              setTab(v as typeof tab);
              setPage(0);
            }}
          >
            <TabsList>
              <TabsTrigger value="VALIDATED">Ready to approve ({ready.data?.totalItems ?? "…"})</TabsTrigger>
              <TabsTrigger value="NEEDS_REVIEW">Needs correction ({attention.data?.totalItems ?? "…"})</TabsTrigger>
            </TabsList>
          </Tabs>
        </div>

        <InvoiceTable
          page={list.data}
          loading={list.isLoading}
          error={list.error}
          onRetry={() => void list.refetch()}
          onSelect={(i) => router.push(`/invoices/${i.id}`)}
          onPage={setPage}
          emptyHint={
            tab === "VALIDATED" ? (
              <span className="flex flex-col items-center gap-2">
                <ShieldCheck className="size-5 text-ok" /> Inbox zero. Nothing is waiting for your approval.
              </span>
            ) : (
              "No invoices need correction right now."
            )
          }
          rowActions={
            tab === "VALIDATED"
              ? (inv) => {
                  const own = inv.uploadedBy === user?.id && user?.role === "APPROVER";
                  return (
                    <span className="inline-flex gap-2">
                      <Button size="sm" variant="destructive" onClick={() => setDecision({ inv, kind: "reject" })}>
                        <XCircle /> Reject
                      </Button>
                      <Button
                        size="sm"
                        variant="success"
                        disabled={own}
                        title={own ? "You cannot approve an invoice you uploaded" : undefined}
                        onClick={() => setDecision({ inv, kind: "approve" })}
                      >
                        <CheckCircle2 /> Approve
                      </Button>
                    </span>
                  );
                }
              : (inv) => (
                  <Button size="sm" variant="outline" onClick={() => router.push(`/invoices/${inv.id}`)}>
                    Review
                  </Button>
                )
          }
        />
      </Card>

      {decision && (
        <DecisionDialog
          key={`${decision.inv.id}-${decision.kind}`}
          invoiceId={decision.inv.id}
          kind={decision.kind}
          summary={`${decision.inv.vendorName ?? decision.inv.originalFilename}${decision.inv.invoiceNumber ? ` · ${decision.inv.invoiceNumber}` : ""} · ${formatMoney(decision.inv.total, decision.inv.currency)}`}
          open
          onOpenChange={(o) => !o && setDecision(null)}
        />
      )}
    </>
  );
}
