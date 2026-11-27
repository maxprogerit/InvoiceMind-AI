"use client";

import { Loader2, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardLabel } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useApprovalRules, useSaveApprovalRules } from "@/lib/api/hooks";

interface Row {
  currency: string;
  autoApproveBelow: string;
  twoApproversAbove: string;
}

const amount = (v: string) => (v.trim() === "" ? undefined : Number(v));

/** Approval rules per currency: automatic below X, one approver up to Y, two approvers above Y. */
export function ApprovalRulesCard() {
  const { data } = useApprovalRules();
  const save = useSaveApprovalRules();
  const [rows, setRows] = useState<Row[]>([]);
  useEffect(() => {
    if (data)
      setRows(
        data.rules.map((r) => ({
          currency: r.currency,
          autoApproveBelow: r.autoApproveBelow == null ? "" : String(r.autoApproveBelow),
          twoApproversAbove: r.twoApproversAbove == null ? "" : String(r.twoApproversAbove),
        })),
      );
  }, [data]);

  const set = (i: number, patch: Partial<Row>) => setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  const submit = () =>
    save.mutate(
      rows
        .filter((r) => r.currency.trim())
        .map((r) => ({
          currency: r.currency.trim().toUpperCase(),
          autoApproveBelow: amount(r.autoApproveBelow),
          twoApproversAbove: amount(r.twoApproversAbove),
        })),
      { onSuccess: () => toast.success("Approval rules saved"), onError: (e) => toast.error(e.message) },
    );

  return (
    <Card className="p-6">
      <CardLabel>Approval rules</CardLabel>
      <p className="mt-3 max-w-2xl text-sm text-muted">
        Per currency. Below the first amount an invoice is approved automatically once every check passed and its vendor is
        confirmed; up to the second amount one approver decides; above it two different approvers. Nobody approves their own
        upload. A currency without a rule always needs one approver. Credit notes count by their amount.
      </p>
      <Table className="mt-4">
        <THead>
          <TR>
            <TH>Currency</TH>
            <TH>Automatic below</TH>
            <TH>Two approvers above</TH>
            <TH className="sr-only">Remove</TH>
          </TR>
        </THead>
        <TBody>
          {rows.length === 0 && (
            <TR>
              <TD colSpan={4} className="py-6 text-center text-sm text-muted">
                No rules: every invoice needs one approver.
              </TD>
            </TR>
          )}
          {rows.map((r, i) => (
            <TR key={i}>
              <TD>
                <Input aria-label="Currency" className="w-24 font-mono uppercase" maxLength={3} value={r.currency} onChange={(e) => set(i, { currency: e.target.value.toUpperCase() })} />
              </TD>
              <TD>
                <Input
                  aria-label={`Automatic below (${r.currency})`}
                  className="w-36 font-mono"
                  inputMode="decimal"
                  placeholder="never"
                  value={r.autoApproveBelow}
                  onChange={(e) => set(i, { autoApproveBelow: e.target.value })}
                />
              </TD>
              <TD>
                <Input
                  aria-label={`Two approvers above (${r.currency})`}
                  className="w-36 font-mono"
                  inputMode="decimal"
                  placeholder="never"
                  value={r.twoApproversAbove}
                  onChange={(e) => set(i, { twoApproversAbove: e.target.value })}
                />
              </TD>
              <TD className="text-right">
                <Button variant="ghost" size="icon" aria-label={`Remove the ${r.currency || "new"} rule`} onClick={() => setRows((rs) => rs.filter((_, j) => j !== i))}>
                  <Trash2 />
                </Button>
              </TD>
            </TR>
          ))}
        </TBody>
      </Table>
      <div className="mt-4 flex gap-2">
        <Button variant="outline" onClick={() => setRows((rs) => [...rs, { currency: "", autoApproveBelow: "", twoApproversAbove: "" }])}>
          <Plus /> Add currency
        </Button>
        <Button onClick={submit} disabled={save.isPending}>
          {save.isPending && <Loader2 className="animate-spin" />} Save rules
        </Button>
      </div>
    </Card>
  );
}
