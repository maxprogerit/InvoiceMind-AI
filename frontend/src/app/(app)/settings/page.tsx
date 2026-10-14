"use client";

import { Check, Copy, Loader2, Mail, Plus, RefreshCw, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardLabel } from "@/components/ui/card";
import { Input, Label } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/misc";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useDeleteManualRate, useEmailIngest, useFxStatus, useRotateEmailAddress, useSetBaseCurrency, useSetManualRate } from "@/lib/api/hooks";
import { useAuth } from "@/lib/auth/store";
import { formatDate } from "@/lib/format";

const COMMON = ["EUR", "USD", "GBP", "CHF", "PLN", "CZK", "SEK", "NOK", "DKK", "RUB", "UAH", "TRY", "JPY", "CNY", "CAD", "AUD"];

function BaseCurrency({ current }: { current: string }) {
  const [value, setValue] = useState(current);
  const save = useSetBaseCurrency();
  useEffect(() => setValue(current), [current]);
  const dirty = value.trim().toUpperCase() !== current;
  return (
    <Card className="p-6">
      <CardLabel>Reporting currency</CardLabel>
      <p className="mt-3 max-w-xl text-sm text-muted">
        Analytics can show everything in one currency. Each invoice is converted at the rate of its own date (the latest business day on or before it).
      </p>
      <form
        className="mt-4 flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate(value, {
            onSuccess: () => toast.success(`Reporting currency is now ${value.toUpperCase()}`),
            onError: (err) => toast.error(err.message),
          });
        }}
      >
        <div>
          <Label htmlFor="base-currency">Currency (ISO 4217)</Label>
          <Input
            id="base-currency"
            list="common-currencies"
            maxLength={3}
            className="w-32 font-mono uppercase"
            value={value}
            onChange={(e) => setValue(e.target.value.toUpperCase())}
          />
          <datalist id="common-currencies">
            {COMMON.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
        </div>
        <Button type="submit" disabled={!dirty || value.trim().length !== 3 || save.isPending}>
          {save.isPending && <Loader2 className="animate-spin" />} Save
        </Button>
      </form>
    </Card>
  );
}

function ManualRates() {
  const { data } = useFxStatus();
  const set = useSetManualRate();
  const remove = useDeleteManualRate();
  const [currency, setCurrency] = useState("");
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [perEur, setPerEur] = useState("");

  return (
    <Card className="p-6">
      <CardLabel>Exchange rates</CardLabel>
      <div className="mt-3 max-w-2xl space-y-2 text-sm text-muted">
        {!data?.ecbEnabled ? (
          <p>Automatic download of ECB reference rates is switched off on this server; add the rates you need below.</p>
        ) : data.ecbCoveredFrom ? (
          <p>
            ECB reference rates are downloaded automatically for the dates your invoices need. Currently stored:{" "}
            <span className="font-mono text-fg">
              {formatDate(data.ecbCoveredFrom)} → {formatDate(data.ecbCoveredTo)}
            </span>
            .
          </p>
        ) : (
          <p>ECB reference rates are downloaded the first time an analysis needs them.</p>
        )}
        {data?.lastError && <p className="text-warn">The last download failed: {data.lastError}. Invoices without a rate are listed as “not converted”.</p>}
        <p>
          The ECB does not publish every currency (the Russian rouble, for example). Add a manual rate for those: it applies from its date until you add a newer one, and
          wins over an ECB rate on the same date.
        </p>
      </div>

      <form
        className="mt-5 flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          set.mutate(
            { currency: currency.trim().toUpperCase(), date, perEur: Number(perEur) },
            {
              onSuccess: () => {
                toast.success("Rate saved");
                setPerEur("");
              },
              onError: (err) => toast.error(err.message),
            },
          );
        }}
      >
        <div>
          <Label htmlFor="rate-currency">Currency</Label>
          <Input id="rate-currency" maxLength={3} className="w-28 font-mono uppercase" value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} placeholder="RUB" />
        </div>
        <div>
          <Label htmlFor="rate-date">Valid from</Label>
          <Input id="rate-date" type="date" className="w-44 font-mono" value={date} onChange={(e) => setDate(e.target.value)} />
        </div>
        <div>
          <Label htmlFor="rate-value">Units per 1 EUR</Label>
          <Input id="rate-value" inputMode="decimal" className="w-36 font-mono" value={perEur} onChange={(e) => setPerEur(e.target.value)} placeholder="100" />
        </div>
        <Button type="submit" disabled={currency.length !== 3 || !date || !(Number(perEur) > 0) || set.isPending}>
          {set.isPending ? <Loader2 className="animate-spin" /> : <Plus />} Add rate
        </Button>
      </form>

      <div className="mt-6 overflow-hidden rounded-control border border-line">
        <Table>
          <THead>
            <TR>
              <TH>Currency</TH>
              <TH>Valid from</TH>
              <TH className="text-right">Units per 1 EUR</TH>
              <TH className="text-right">
                <span className="sr-only">Actions</span>
              </TH>
            </TR>
          </THead>
          <TBody>
            {!data && (
              <TR>
                <TD colSpan={4}>
                  <Skeleton className="h-6" />
                </TD>
              </TR>
            )}
            {data?.manualRates.length === 0 && (
              <TR>
                <TD colSpan={4} className="py-6 text-center text-sm text-dim">
                  No manual rates yet.
                </TD>
              </TR>
            )}
            {data?.manualRates.map((r) => (
              <TR key={r.id}>
                <TD className="font-mono">{r.currency}</TD>
                <TD className="font-mono text-muted">{formatDate(r.date)}</TD>
                <TD className="num text-right">{Number(r.perEur)}</TD>
                <TD className="text-right">
                  <button
                    type="button"
                    aria-label={`Delete the ${r.currency} rate from ${r.date}`}
                    onClick={() => remove.mutate(r.id, { onError: (err) => toast.error(err.message) })}
                    className="grid size-8 place-items-center rounded-md text-dim hover:bg-bad/10 hover:text-bad"
                  >
                    <Trash2 className="size-4" />
                  </button>
                </TD>
              </TR>
            ))}
          </TBody>
        </Table>
      </div>
    </Card>
  );
}

function EmailIngestCard() {
  const { data } = useEmailIngest();
  const rotate = useRotateEmailAddress();
  const [copied, setCopied] = useState(false);
  return (
    <Card className="p-6">
      <CardLabel>E-mail ingestion</CardLabel>
      <p className="mt-3 max-w-2xl text-sm text-muted">
        Forward or send invoices to your organisation&apos;s address. Attached PDFs and images are imported like uploads and go through the same checks and approval.
        The sender must be a user of this organisation; their e-mail address decides who the document is attributed to.
      </p>
      {!data ? (
        <Skeleton className="mt-4 h-10" />
      ) : !data.enabled ? (
        <p className="mt-4 rounded-control border border-line bg-bg/40 p-4 text-sm text-muted">
          Not switched on for this server. Set <code className="font-mono text-xs text-fg">MAIL_INGEST_ENABLED</code>, <code className="font-mono text-xs text-fg">MAIL_INGEST_ADDRESS</code> and the
          <code className="font-mono text-xs text-fg"> MAIL_IMAP_*</code> variables to a mailbox you control, then restart the backend.
        </p>
      ) : (
        <>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <div className="flex min-w-0 items-center gap-2 rounded-control border border-line bg-bg/60 px-3 py-2">
              <Mail className="size-4 shrink-0 text-dim" />
              <code className="truncate font-mono text-sm" data-testid="ingest-address">
                {data.address}
              </code>
            </div>
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                void navigator.clipboard?.writeText(data.address ?? "").then(() => setCopied(true));
              }}
            >
              {copied ? <Check /> : <Copy />} {copied ? "Copied" : "Copy"}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              disabled={rotate.isPending}
              onClick={() => {
                if (window.confirm("Create a new address? The current one stops working immediately.")) {
                  rotate.mutate(undefined, { onSuccess: () => toast.success("New address created"), onError: (e) => toast.error(e.message) });
                }
              }}
            >
              <RefreshCw /> New address
            </Button>
          </div>
          {data.requireAuthResults && <p className="mt-2 text-xs text-dim">Senders must be verified by the mail server (DKIM or SPF pass).</p>}
          <div className="mt-5 overflow-hidden rounded-control border border-line">
            <Table>
              <THead>
                <TR>
                  <TH>Received</TH>
                  <TH>From</TH>
                  <TH>Subject</TH>
                  <TH>Result</TH>
                </TR>
              </THead>
              <TBody>
                {data.recent.length === 0 && (
                  <TR>
                    <TD colSpan={4} className="py-6 text-center text-sm text-dim">
                      No messages yet. The mailbox is checked every minute.
                    </TD>
                  </TR>
                )}
                {data.recent.map((m) => (
                  <TR key={m.id}>
                    <TD className="whitespace-nowrap font-mono text-xs text-muted">{new Date(m.receivedAt).toLocaleString()}</TD>
                    <TD className="max-w-[200px] truncate text-sm">{m.from ?? "—"}</TD>
                    <TD className="max-w-[240px] truncate text-sm text-muted">{m.subject ?? "—"}</TD>
                    <TD className="text-xs">
                      <Badge tone={m.status === "ACCEPTED" ? "ok" : "bad"}>
                        {m.status === "ACCEPTED" ? `${m.invoicesCreated} imported` : "rejected"}
                      </Badge>
                      {m.reason && <div className="mt-1 max-w-[320px] text-dim">{m.reason}</div>}
                    </TD>
                  </TR>
                ))}
              </TBody>
            </Table>
          </div>
        </>
      )}
    </Card>
  );
}

export default function SettingsPage() {
  const router = useRouter();
  const me = useAuth((s) => s.user);
  const admin = me?.role === "ADMIN";
  useEffect(() => {
    if (me && !admin) router.replace("/invoices");
  }, [me, admin, router]);
  const { data } = useFxStatus(admin);
  if (!admin) return null;

  return (
    <>
      <PageHeader title="Settings" subtitle="Organisation-wide preferences for money and reporting." />
      <div className="grid gap-6">{data ? <BaseCurrency current={data.baseCurrency} /> : <Skeleton className="h-40" />}</div>
      <div className="mt-6">
        <EmailIngestCard />
      </div>
      <div className="mt-6">
        <ManualRates />
      </div>
    </>
  );
}
