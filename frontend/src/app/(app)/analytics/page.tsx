"use client";

import { useState } from "react";

import { SpendByMonthChart, SpendByVendorChart, StatusBreakdown, VolumeChart } from "@/components/analytics/charts";
import { PageHeader } from "@/components/layout/page-header";
import { Card } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/misc";
import { useByStatus, useSpendByMonth, useSpendByVendor, useSummary, useVolume } from "@/lib/api/hooks";
import { formatDuration, formatMoney, formatNumber, formatPercent } from "@/lib/format";

function Kpi({ label, value, hint, loading }: { label: string; value: string; hint?: string; loading?: boolean }) {
  return (
    <Card className="p-5">
      <div className="label-mono">{label}</div>
      {loading ? <Skeleton className="mt-3 h-8 w-28" /> : <div className="num mt-2.5 text-[28px] font-bold leading-none tracking-tight">{value}</div>}
      {hint && <div className="mt-2 text-xs text-muted">{hint}</div>}
    </Card>
  );
}

export default function AnalyticsPage() {
  const summary = useSummary();
  const s = summary.data;
  const currencies = [...new Set([...(s?.approvedSpend ?? []), ...(s?.pendingAmount ?? [])].map((m) => m.currency))];
  const [picked, setPicked] = useState<string>();
  const currency = picked && currencies.includes(picked) ? picked : s?.defaultCurrency;

  const vendors = useSpendByVendor(currency);
  const months = useSpendByMonth(currency);
  const byStatus = useByStatus();
  const volume = useVolume(30);

  const approved = s?.approvedSpend.find((m) => m.currency === currency);
  const pending = s?.pendingAmount.find((m) => m.currency === currency);

  return (
    <>
      <PageHeader
        title="Analysis"
        subtitle="Live numbers computed from your invoices. Nothing here is estimated."
        actions={
          currencies.length > 1 && currency ? (
            <Select value={currency} onValueChange={setPicked}>
              <SelectTrigger className="w-28 font-mono" aria-label="Currency">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {currencies.map((c) => (
                  <SelectItem key={c} value={c}>
                    {c}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          ) : null
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Kpi label="Approved spend" loading={summary.isLoading} value={approved ? formatMoney(approved.amount, approved.currency) : "—"} hint={approved ? `${approved.invoices} approved invoices` : "Nothing approved yet"} />
        <Kpi label="Awaiting decision" loading={summary.isLoading} value={s ? String(s.awaitingDecision) : "—"} hint={pending ? `${formatMoney(pending.amount, pending.currency)} pending` : undefined} />
        <Kpi label="Avg. processing time" loading={summary.isLoading} value={formatDuration(s?.avgProcessingSeconds)} hint="Upload to validated" />
        <Kpi
          label="Needed human review"
          loading={summary.isLoading}
          value={formatPercent(s?.reviewRate)}
          hint={s?.autoValidatedRate != null ? `${formatPercent(s.autoValidatedRate)} passed automatically` : "No processed invoices yet"}
        />
        <Kpi label="Invoices" loading={summary.isLoading} value={s ? formatNumber(s.totalInvoices) : "—"} hint={s ? `${s.approved} approved · ${s.rejected} rejected` : undefined} />
        <Kpi label="Failure rate" loading={summary.isLoading} value={formatPercent(s?.failureRate, 1)} hint={s ? `${s.failed} failed` : undefined} />
        <Kpi label="OCR confidence" loading={summary.isLoading} value={formatPercent(s?.avgOcrConfidence, 1)} hint="Mean over all documents" />
        <Kpi
          label="AI cost"
          loading={summary.isLoading}
          value={s ? `$${Number(s.costUsd).toFixed(4)}` : "—"}
          hint={s ? `${formatNumber(s.inputTokens + s.outputTokens)} tokens · $${Number(s.avgCostPerInvoiceUsd).toFixed(5)} per invoice` : undefined}
        />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <SpendByMonthChart data={months.data} loading={months.isLoading || summary.isLoading} />
        <SpendByVendorChart data={vendors.data} loading={vendors.isLoading || summary.isLoading} />
        <VolumeChart data={volume.data} loading={volume.isLoading} />
        <StatusBreakdown data={byStatus.data} loading={byStatus.isLoading} />
      </div>
    </>
  );
}
