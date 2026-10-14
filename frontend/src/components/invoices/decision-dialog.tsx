"use client";

import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label, Textarea } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { useDecision } from "@/lib/api/hooks";

/** Approve (optional comment) or reject (comment required). Used on the review page and the approvals queue. */
export function DecisionDialog({
  invoiceId,
  kind,
  summary,
  open,
  onOpenChange,
  onDone,
}: {
  invoiceId: string;
  kind: "approve" | "reject";
  summary: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onDone?: () => void;
}) {
  const [comment, setComment] = useState("");
  const mutation = useDecision(invoiceId, kind);
  const approve = kind === "approve";
  const tooShort = !approve && comment.trim().length < 3;

  function submit() {
    mutation.mutate(comment.trim() || undefined, {
      onSuccess: () => {
        toast.success(approve ? "Invoice approved" : "Invoice rejected");
        setComment("");
        onOpenChange(false);
        onDone?.();
      },
      onError: (e) => toast.error(e instanceof ApiError ? e.message : "The decision could not be saved"),
    });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2.5">
            {approve ? <CheckCircle2 className="size-5 text-ok" /> : <XCircle className="size-5 text-bad" />}
            {approve ? "Approve invoice" : "Reject invoice"}
          </DialogTitle>
          <DialogDescription>{summary}</DialogDescription>
        </DialogHeader>
        <Label htmlFor={`comment-${kind}`}>{approve ? "Comment (optional)" : "Reason (required)"}</Label>
        <Textarea
          id={`comment-${kind}`}
          autoFocus
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          maxLength={2000}
          placeholder={approve ? "e.g. Matches purchase order 4711" : "Explain what is wrong so the uploader can fix it"}
        />
        <p className="mt-2 text-xs text-dim">Your decision and comment are recorded in the audit log.</p>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button variant={approve ? "success" : "destructive"} disabled={mutation.isPending || tooShort} onClick={submit}>
            {mutation.isPending && <Loader2 className="animate-spin" />}
            {approve ? "Approve" : "Reject"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
