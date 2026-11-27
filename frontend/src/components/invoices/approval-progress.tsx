import { BadgeCheck, Users } from "lucide-react";

import type { InvoiceDetail } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

/** "Approval 1 of 2" under the organisation's rules, who approved when, or "approved automatically". */
export function ApprovalProgress({ invoice }: { invoice: InvoiceDetail }) {
  const a = invoice.approval;
  if (invoice.status === "REJECTED" || invoice.status === "FAILED") return null;
  if (a.autoApproved) {
    return (
      <p className="mb-6 flex items-center gap-2 text-sm text-ok">
        <BadgeCheck className="size-4" aria-hidden /> Approved automatically. {a.rule}.
      </p>
    );
  }
  if (invoice.status !== "VALIDATED" && invoice.status !== "APPROVED" && invoice.status !== "NEEDS_REVIEW") return null;
  const required = Math.max(a.required, 1);
  return (
    <div className="mb-6 rounded-card border border-line px-5 py-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <Users className="size-4 text-muted" aria-hidden />
        <strong>
          Approvals {Math.min(a.given.length, required)} of {required}
        </strong>
        <span className="text-muted">· {a.rule}</span>
      </div>
      {a.given.length > 0 && (
        <ul className="mt-2 space-y-1 pl-6 text-[13px] text-muted">
          {a.given.map((g, i) => (
            <li key={i}>
              {g.name} · {formatDateTime(g.at)}
              {g.comment ? <span className="text-fg"> · “{g.comment}”</span> : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
