"use client";

import { AlertTriangle, BadgeCheck, CopyX, Loader2, Store } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { useDuplicateDecision, useVerifyVendor } from "@/lib/api/hooks";
import type { InvoiceDetail } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

/**
 * Explains the duplicate rules in plain words. DUPLICATE (same vendor + number, or an identical file) blocks approval;
 * POSSIBLE_DUPLICATE (same vendor + total, date close by, different number) waits for a person's decision.
 */
export function DuplicateBanner({ invoice }: { invoice: InvoiceDetail }) {
  const d = invoice.duplicate;
  const decide = useDuplicateDecision(invoice.id);
  const [comment, setComment] = useState("");
  const original = d.ofId ? (
    <Link href={`/invoices/${d.ofId}`} className="font-medium underline underline-offset-2">
      {d.ofLabel ?? "the earlier invoice"}
    </Link>
  ) : (
    "an earlier invoice"
  );

  if (d.status === "DUPLICATE") {
    return (
      <div role="alert" className="mb-6 rounded-card border border-bad/30 bg-bad/10 px-5 py-4 text-sm text-bad">
        <div className="flex items-start gap-3">
          <CopyX className="mt-0.5 size-4 shrink-0" aria-hidden />
          <div>
            <strong>Duplicate of {original}.</strong>{" "}
            {d.reusedFrom
              ? "The identical file was uploaded before, so its result was reused and the AI was not called again."
              : "Same vendor and same invoice number."}{" "}
            It cannot be approved; reject or delete it.
          </div>
        </div>
      </div>
    );
  }

  if (d.status === "POSSIBLE_DUPLICATE") {
    const run = (decision: "NOT_DUPLICATE" | "DUPLICATE") =>
      decide.mutate(
        { decision, comment: comment.trim() || undefined },
        {
          onSuccess: () => toast.success(decision === "NOT_DUPLICATE" ? "Marked as a separate invoice" : "Rejected as a duplicate"),
          onError: (e) => toast.error(e.message),
        },
      );
    return (
      <div role="alert" className="mb-6 rounded-card border border-warn/30 bg-warn/10 px-5 py-4 text-sm">
        <div className="flex items-start gap-3 text-warn">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
          <div>
            <strong>Possible duplicate of {original}</strong>
            {d.ofStatus ? <span className="font-mono text-xs"> ({d.ofStatus.toLowerCase().replace("_", " ")})</span> : null}:
            same vendor and total, dated within a few days, but a different invoice number. Check with the vendor, then decide.
          </div>
        </div>
        {invoice.permissions.canDecideDuplicate && (
          <div className="mt-4 flex flex-wrap items-end gap-3 pl-7">
            <div className="min-w-56 flex-1">
              <Label htmlFor="dup-comment" className="text-xs">
                Note (optional)
              </Label>
              <Input id="dup-comment" value={comment} maxLength={1000} onChange={(e) => setComment(e.target.value)} placeholder="e.g. second delivery, confirmed by phone" />
            </div>
            <Button variant="outline" disabled={decide.isPending} onClick={() => run("NOT_DUPLICATE")}>
              {decide.isPending && <Loader2 className="animate-spin" />} It is a separate invoice
            </Button>
            <Button variant="destructive" disabled={decide.isPending} onClick={() => run("DUPLICATE")}>
              Reject as duplicate
            </Button>
          </div>
        )}
      </div>
    );
  }

  if (d.clearedByName) {
    return (
      <p className="mb-6 text-sm text-muted">
        {d.clearedByName} marked this as a separate invoice (not a duplicate){d.clearedAt ? ` on ${formatDateTime(d.clearedAt)}` : ""}.
      </p>
    );
  }
  return null;
}

/** New vendors come from documents, so a person confirms them once; until then the invoice cannot be approved. */
export function VendorBanner({ invoice }: { invoice: InvoiceDetail }) {
  const verify = useVerifyVendor();
  const v = invoice.vendor;
  if (!v || v.status !== "UNVERIFIED") return null;
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-card border border-info/30 bg-info/10 px-5 py-4 text-sm text-info">
      <div className="flex items-start gap-3">
        <Store className="mt-0.5 size-4 shrink-0" aria-hidden />
        <div>
          <strong>New vendor: {v.name}</strong>
          {v.taxId ? <span className="font-mono text-xs"> · tax id {v.taxId}</span> : null}. It was created from this document and is not
          confirmed yet. Confirm it is a real supplier before the invoice is approved.
        </div>
      </div>
      <div className="flex gap-2">
        <Button variant="ghost" asChild>
          <Link href="/vendors">Vendors</Link>
        </Button>
        <Button
          variant="outline"
          disabled={verify.isPending}
          onClick={() => verify.mutate(v.id, { onSuccess: () => toast.success(`${v.name} confirmed`), onError: (e) => toast.error(e.message) })}
        >
          {verify.isPending ? <Loader2 className="animate-spin" /> : <BadgeCheck />} Confirm vendor
        </Button>
      </div>
    </div>
  );
}

/** Why the approve button is missing, straight from the server's approval rules. */
export function ApprovalBlocked({ invoice }: { invoice: InvoiceDetail }) {
  const reason = invoice.permissions.approvalBlockedReason;
  if (!reason || invoice.duplicate.status !== "NONE") return null; // the duplicate banner already explains it
  return <p className="mb-6 text-sm text-muted">Approval: {reason}.</p>;
}
