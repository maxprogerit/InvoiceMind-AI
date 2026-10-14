"use client";

import { Lightbulb, Loader2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardLabel } from "@/components/ui/card";
import { ApiError } from "@/lib/api/client";
import { useExplainInvoice } from "@/lib/api/hooks";
import type { InvoiceDetail } from "@/lib/api/types";

import { AnswerView } from "./answer-view";

/** "Why was this flagged?": the model explains the invoice's findings using the organisation's own rules documents. */
export function ExplainPanel({ invoice }: { invoice: InvoiceDetail }) {
  const explain = useExplainInvoice(invoice.id);
  const relevant = invoice.issues.length > 0 || invoice.status === "NEEDS_REVIEW" || invoice.status === "REJECTED";
  if (!relevant) return null;

  return (
    <Card className="p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <CardLabel>Explain</CardLabel>
          <p className="mt-2 text-sm text-muted">Ask what your own policies say about the findings above, with sources.</p>
        </div>
        <Button
          variant="outline"
          disabled={explain.isPending}
          onClick={() =>
            explain.mutate(undefined, {
              onError: (e) =>
                toast.error(e instanceof ApiError && e.status === 503 ? "The AI service is not available right now." : e.message),
            })
          }
        >
          {explain.isPending ? <Loader2 className="animate-spin" /> : <Lightbulb />} Why was this flagged?
        </Button>
      </div>
      {explain.data && (
        <div className="mt-5 border-t border-line pt-5">
          <AnswerView answer={explain.data} />
        </div>
      )}
    </Card>
  );
}
