"use client";

import { Download, Loader2, Send } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardLabel } from "@/components/ui/card";
import { Input, Label } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ApiError, downloadBlob, fetchBlob } from "@/lib/api/client";
import { useAccountingBatches, useAccountingExport, useAccountingPending } from "@/lib/api/hooks";
import { formatDateTime } from "@/lib/format";

type Target = "XERO" | "QBO";
type DateStyle = "DMY" | "MDY" | "ISO";
const STORAGE_KEY = "invoicemind.accounting-export";

interface Prefs {
  target: Target;
  account: string;
  taxCode: string;
  dateStyle: DateStyle;
}

function loadPrefs(): Prefs {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw) as Prefs;
  } catch {
    /* private mode or blocked storage: defaults */
  }
  return { target: "XERO", account: "", taxCode: "", dateStyle: "DMY" };
}

async function download(batchId: string) {
  const { blob, filename } = await fetchBlob(`/api/v1/export/accounting/batches/${batchId}/file`);
  downloadBlob(blob, filename ?? "bills.csv");
}

/** Approved, not yet exported invoices -> a Xero or QuickBooks Online bill import; each invoice leaves once. */
export function AccountingExportCard() {
  const pending = useAccountingPending();
  const batches = useAccountingBatches();
  const run = useAccountingExport();
  const [prefs, setPrefs] = useState<Prefs>({ target: "XERO", account: "", taxCode: "", dateStyle: "DMY" });
  const [skipped, setSkipped] = useState<{ label: string; reason: string }[]>([]);
  useEffect(() => setPrefs(loadPrefs()), []);
  const set = (p: Partial<Prefs>) =>
    setPrefs((old) => {
      const next = { ...old, ...p };
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      } catch {
        /* remembering is a convenience only */
      }
      return next;
    });

  const waiting = pending.data?.approvedNotExported ?? 0;
  const submit = () =>
    run.mutate(
      { target: prefs.target, account: prefs.account.trim(), taxCode: prefs.taxCode.trim() || undefined, dateStyle: prefs.dateStyle },
      {
        onSuccess: async (r) => {
          setSkipped(r.skipped);
          toast.success(`${r.invoices} invoice${r.invoices === 1 ? "" : "s"} exported`);
          await download(r.batchId);
        },
        onError: (e) => toast.error(e instanceof ApiError ? e.message : "Export failed"),
      },
    );

  return (
    <Card className="p-6">
      <CardLabel>To accounting</CardLabel>
      <p className="mt-3 text-sm text-muted">
        Approved invoices as a bill import for Xero or QuickBooks Online. Each invoice is exported once and then marked; earlier files stay
        downloadable below. An invoice&apos;s expense category is used as its account.
      </p>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <div>
          <Label htmlFor="acct-target">System</Label>
          <Select value={prefs.target} onValueChange={(v) => set({ target: v as Target, dateStyle: v === "QBO" ? "MDY" : prefs.dateStyle })}>
            <SelectTrigger id="acct-target">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="XERO">Xero (bills import)</SelectItem>
              <SelectItem value="QBO">QuickBooks Online (import bills)</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div>
          <Label htmlFor="acct-dates">Date format</Label>
          <Select value={prefs.dateStyle} onValueChange={(v) => set({ dateStyle: v as DateStyle })}>
            <SelectTrigger id="acct-dates">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="DMY">31/12/2026</SelectItem>
              <SelectItem value="MDY">12/31/2026</SelectItem>
              <SelectItem value="ISO">2026-12-31</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div>
          <Label htmlFor="acct-account">Default account</Label>
          <Input id="acct-account" value={prefs.account} maxLength={100} placeholder={prefs.target === "XERO" ? "e.g. 429" : "e.g. Office Supplies"} onChange={(e) => set({ account: e.target.value })} />
        </div>
        <div>
          <Label htmlFor="acct-tax">Tax code</Label>
          <Input id="acct-tax" value={prefs.taxCode} maxLength={100} placeholder="as named in your accounting system" onChange={(e) => set({ taxCode: e.target.value })} />
        </div>
      </div>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <span className="text-sm text-muted" aria-live="polite">
          {waiting === 0 ? "No approved invoices are waiting." : `${waiting} approved invoice${waiting === 1 ? "" : "s"} waiting.`}
        </span>
        <Button onClick={submit} disabled={run.isPending || waiting === 0 || !prefs.account.trim()}>
          {run.isPending ? <Loader2 className="animate-spin" /> : <Send />} Export to {prefs.target === "XERO" ? "Xero" : "QuickBooks"}
        </Button>
      </div>
      {skipped.length > 0 && (
        <ul className="mt-4 space-y-1 rounded-control border border-warn/30 bg-warn/10 px-4 py-3 text-sm text-warn">
          {skipped.map((s) => (
            <li key={s.label}>
              <strong>{s.label}</strong>: {s.reason}
            </li>
          ))}
        </ul>
      )}
      {(batches.data?.length ?? 0) > 0 && (
        <ul className="mt-5 divide-y divide-line/60 border-t border-line text-sm">
          {batches.data!.map((b) => (
            <li key={b.id} className="flex items-center justify-between gap-3 py-2">
              <span>
                {b.target === "XERO" ? "Xero" : "QuickBooks"} · {b.invoices} invoice{b.invoices === 1 ? "" : "s"}
                <span className="font-mono text-xs text-dim">
                  {" "}
                  · {formatDateTime(b.createdAt)}
                  {b.createdByName ? ` · ${b.createdByName}` : ""}
                </span>
              </span>
              <Button variant="ghost" size="sm" onClick={() => void download(b.id).catch((e) => toast.error(e.message))}>
                <Download /> CSV
              </Button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
