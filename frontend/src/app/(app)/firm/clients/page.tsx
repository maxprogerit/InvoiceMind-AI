"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Building2, Loader2, Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/misc";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { ApiError } from "@/lib/api/client";
import { useCreateClient, useFirm, useFirmClients, useSetClientActive, useSwitchOrganization } from "@/lib/api/hooks";
import type { ClientSummary } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/store";
import { formatMoney, formatNumber } from "@/lib/format";
import { cn } from "@/lib/utils";

const schema = z.object({
  name: z.string().trim().min(1, "Required").max(200),
  baseCurrency: z.string().trim().regex(/^[A-Za-z]{3}$/, "Three letters, e.g. EUR"),
});

function AddClient({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const create = useCreateClient();
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { name: "", baseCurrency: "EUR" } });
  const e = form.formState.errors;

  const submit = form.handleSubmit((values) =>
    create.mutate(
      { name: values.name, baseCurrency: values.baseCurrency.toUpperCase() },
      {
        onSuccess: () => {
          toast.success(`${values.name} was added`);
          form.reset();
          onOpenChange(false);
        },
        onError: (err) => {
          if (err instanceof ApiError && err.code === "client_exists") form.setError("name", { message: "You already have a client with this name" });
          else if (err instanceof ApiError && err.fieldErrors?.baseCurrency) form.setError("baseCurrency", { message: err.fieldErrors.baseCurrency });
          else if (err instanceof ApiError && err.code === "invalid_currency") form.setError("baseCurrency", { message: "Unknown currency code" });
          else toast.error(err.message);
        },
      },
    ),
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a client company</DialogTitle>
          <DialogDescription>Its invoices, team access and settings stay separate from your other clients.</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <div>
            <Label htmlFor="c-name">Company name</Label>
            <Input id="c-name" aria-invalid={!!e.name} {...form.register("name")} />
            {e.name && <p className="mt-1 text-xs text-bad">{e.name.message}</p>}
          </div>
          <div>
            <Label htmlFor="c-currency">Reporting currency</Label>
            <Input id="c-currency" maxLength={3} className="w-28 uppercase" aria-invalid={!!e.baseCurrency} {...form.register("baseCurrency")} />
            {e.baseCurrency && <p className="mt-1 text-xs text-bad">{e.baseCurrency.message}</p>}
            <p className="mt-1.5 text-xs text-dim">Analytics for this client are reported in this currency.</p>
          </div>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={create.isPending}>
              {create.isPending && <Loader2 className="animate-spin" />} Add client
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function Count({ n, tone }: { n: number; tone: "warn" | "bad" | "accent" }) {
  if (n === 0) return <span className="text-dim">0</span>;
  return <Badge tone={tone}>{formatNumber(n)}</Badge>;
}

export default function FirmClientsPage() {
  const router = useRouter();
  const me = useAuth((s) => s.user);
  const inFirm = !!me?.firmRole;
  useEffect(() => {
    if (me && !inFirm) router.replace("/invoices");
  }, [me, inFirm, router]);

  const firm = useFirm({ enabled: inFirm });
  const { data, isLoading, error } = useFirmClients();
  const switchOrg = useSwitchOrganization();
  const setActive = useSetClientActive();
  const [adding, setAdding] = useState(false);
  if (!inFirm) return null;
  const admin = me?.firmRole === "FIRM_ADMIN";

  function open(c: ClientSummary) {
    if (!c.active) return;
    if (c.id === me?.organizationId) {
      router.push("/invoices");
      return;
    }
    switchOrg.mutate(c.id, {
      onSuccess: () => router.push("/invoices"),
      onError: (e) => toast.error(e.message),
    });
  }

  const toggle = (c: ClientSummary) =>
    setActive.mutate(
      { id: c.id, active: !c.active },
      { onSuccess: () => toast.success(c.active ? `${c.name} was deactivated` : `${c.name} is active again`), onError: (e) => toast.error(e.message) },
    );

  const limit = firm.data?.documentLimit;
  return (
    <>
      <PageHeader
        title="Firm clients"
        subtitle={firm.data ? `${firm.data.name}: open a client to work in its invoices.` : "Open a client to work in its invoices."}
        actions={
          admin && (
            <Button onClick={() => setAdding(true)}>
              <Plus /> Add client
            </Button>
          )
        }
      />
      {firm.data && limit != null && (
        <p className="mb-4 font-mono text-xs text-muted">
          Documents this month, all clients together: {formatNumber(firm.data.documentsThisMonth)} of {formatNumber(limit)}
        </p>
      )}
      <Card className="overflow-hidden">
        {error ? (
          <p className="p-10 text-center text-sm text-bad">{error.message}</p>
        ) : (
          <Table>
            <THead>
              <TR>
                <TH>Client</TH>
                <TH className="text-right">Documents</TH>
                <TH className="text-right">In review</TH>
                <TH className="text-right">Awaiting approval</TH>
                <TH className="text-right">Failed</TH>
                <TH className="text-right">Approved this month</TH>
                {admin && <TH className="w-28" />}
              </TR>
            </THead>
            <TBody>
              {isLoading &&
                Array.from({ length: 3 }).map((_, i) => (
                  <TR key={i}>
                    <TD colSpan={admin ? 7 : 6}>
                      <Skeleton className="h-6" />
                    </TD>
                  </TR>
                ))}
              {data?.map((c) => {
                const current = c.id === me?.organizationId;
                return (
                  <TR
                    key={c.id}
                    data-testid="client-row"
                    onClick={() => open(c)}
                    className={cn(c.active ? "cursor-pointer hover:bg-surface/60" : "opacity-60")}
                  >
                    <TD>
                      <button
                        type="button"
                        disabled={!c.active || switchOrg.isPending}
                        onClick={(ev) => {
                          ev.stopPropagation();
                          open(c);
                        }}
                        className="flex items-center gap-2.5 text-left font-medium hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
                      >
                        <Building2 className="size-4 shrink-0 text-dim" aria-hidden />
                        {c.name}
                        {current && <Badge tone="accent">current</Badge>}
                        {!c.active && <Badge>inactive</Badge>}
                      </button>
                      <div className="ml-[26px] font-mono text-[11px] text-dim">{c.baseCurrency}</div>
                    </TD>
                    <TD className="text-right font-mono">{formatNumber(c.documents)}</TD>
                    <TD className="text-right">
                      <Count n={c.inReview} tone="warn" />
                    </TD>
                    <TD className="text-right">
                      <Count n={c.awaitingApproval} tone="accent" />
                    </TD>
                    <TD className="text-right">
                      <Count n={c.failed} tone="bad" />
                    </TD>
                    <TD className="text-right">
                      <div className="font-mono">{formatMoney(c.monthToDateTotal, c.baseCurrency)}</div>
                      {c.monthToDateOtherCurrency > 0 && (
                        <div className="font-mono text-[11px] text-dim" title="Approved invoices in other currencies are not converted into this total">
                          + {c.monthToDateOtherCurrency} in other currencies
                        </div>
                      )}
                    </TD>
                    {admin && (
                      <TD className="text-right">
                        {!current && (
                          <Button
                            size="sm"
                            variant="outline"
                            disabled={setActive.isPending}
                            onClick={(ev) => {
                              ev.stopPropagation();
                              toggle(c);
                            }}
                          >
                            {c.active ? "Deactivate" : "Activate"}
                          </Button>
                        )}
                      </TD>
                    )}
                  </TR>
                );
              })}
            </TBody>
          </Table>
        )}
      </Card>
      <AddClient open={adding} onOpenChange={setAdding} />
    </>
  );
}
