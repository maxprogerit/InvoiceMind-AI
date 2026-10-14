import { AlertOctagon, AlertTriangle, CheckCircle2, ShieldAlert } from "lucide-react";

import { Card, CardLabel } from "@/components/ui/card";
import type { InvoiceDetail } from "@/lib/api/types";
import { cn } from "@/lib/utils";

/** What the deterministic validator (and the AI's own flags) found, errors first. */
export function IssuesPanel({ invoice }: { invoice: InvoiceDetail }) {
  const issues = [...invoice.issues].sort((a, b) => (a.severity === b.severity ? 0 : a.severity === "ERROR" ? -1 : 1));
  const clean = issues.length === 0 && !["UPLOADED", "PROCESSING", "EXTRACTED", "FAILED"].includes(invoice.status);

  return (
    <Card className="p-6">
      <CardLabel>Validation</CardLabel>

      {invoice.injectionSuspected && (
        <div className="mt-4 flex gap-3 rounded-control border border-bad/40 bg-bad/10 p-3 text-sm text-bad" role="alert">
          <ShieldAlert className="mt-0.5 size-4 shrink-0" />
          <p>
            <strong>Possible prompt injection.</strong> This document contains text that looks like instructions to an AI system. Every
            extracted value was double-checked, but verify them against the document.
          </p>
        </div>
      )}

      {clean && (
        <p className="mt-4 flex items-center gap-2 text-sm text-ok">
          <CheckCircle2 className="size-4" /> All checks passed: totals add up, dates and currency are valid, no duplicate found.
        </p>
      )}
      {issues.length === 0 && !clean && <p className="mt-4 text-sm text-dim">Checks run once processing has finished.</p>}

      {issues.length > 0 && (
        <ul className="mt-4 space-y-2.5">
          {issues.map((issue, i) => {
            const error = issue.severity === "ERROR";
            return (
              <li
                key={`${issue.code}-${i}`}
                className={cn("flex gap-3 rounded-control border p-3 text-sm", error ? "border-bad/30 bg-bad/5" : "border-warn/30 bg-warn/5")}
              >
                {error ? <AlertOctagon className="mt-0.5 size-4 shrink-0 text-bad" /> : <AlertTriangle className="mt-0.5 size-4 shrink-0 text-warn" />}
                <div className="min-w-0">
                  <p>{issue.message}</p>
                  <p className="mt-1 font-mono text-[10px] uppercase tracking-wider text-dim">
                    {issue.code}
                    {issue.field ? ` · ${issue.field}` : ""}
                  </p>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
