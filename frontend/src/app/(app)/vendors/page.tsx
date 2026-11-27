"use client";

import { BadgeCheck, Loader2, Pencil, Plus, X } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";

import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/misc";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { useAddAlias, useForgetMemory, useRemoveAlias, useUpdateVendor, useVendors, useVerifyVendor } from "@/lib/api/hooks";
import type { Vendor } from "@/lib/api/types";
import { describeMemory } from "@/lib/vendor-memory";
import { formatDate } from "@/lib/format";

function EditVendor({ vendor, onClose }: { vendor: Vendor; onClose: () => void }) {
  const update = useUpdateVendor();
  const addAlias = useAddAlias();
  const removeAlias = useRemoveAlias();
  const forget = useForgetMemory();
  const [name, setName] = useState(vendor.name);
  const [taxId, setTaxId] = useState(vendor.taxId ?? "");
  const [currency, setCurrency] = useState(vendor.defaultCurrency ?? "");
  const [terms, setTerms] = useState(vendor.paymentTermsDays == null ? "" : String(vendor.paymentTermsDays));
  const [alias, setAlias] = useState("");

  const save = () =>
    update.mutate(
      { id: vendor.id, name: name.trim(), taxId: taxId.trim(), defaultCurrency: currency.trim(), paymentTermsDays: terms.trim() === "" ? undefined : Number(terms) },
      { onSuccess: () => (toast.success("Vendor saved"), onClose()), onError: (e) => toast.error(e.message) },
    );
  const add = () =>
    alias.trim() &&
    addAlias.mutate({ id: vendor.id, alias: alias.trim() }, { onSuccess: () => setAlias(""), onError: (e) => toast.error(e.message) });

  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit vendor</DialogTitle>
          <DialogDescription>
            Matching uses the tax id first, then the name or an alias. Aliases are other names the same vendor prints on its invoices.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div>
            <Label htmlFor="v-name">Name</Label>
            <Input id="v-name" value={name} maxLength={300} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="grid grid-cols-[1fr_8rem] gap-3">
            <div>
              <Label htmlFor="v-tax">Tax / VAT id</Label>
              <Input id="v-tax" value={taxId} maxLength={60} onChange={(e) => setTaxId(e.target.value)} placeholder="e.g. DE123456789" />
            </div>
            <div>
              <Label htmlFor="v-cur">Currency</Label>
              <Input id="v-cur" value={currency} maxLength={3} onChange={(e) => setCurrency(e.target.value.toUpperCase())} placeholder="EUR" />
            </div>
          </div>
          <div>
            <Label htmlFor="v-terms">Payment terms (days)</Label>
            <Input id="v-terms" className="w-32 font-mono" inputMode="numeric" value={terms} onChange={(e) => setTerms(e.target.value.replace(/\D/g, "").slice(0, 3))} placeholder="none" />
            <p className="mt-1 text-xs text-dim">When an invoice prints no due date: due date = invoice date + these days.</p>
          </div>
          <div>
            <Label htmlFor="v-alias">Aliases</Label>
            <div className="mb-2 flex flex-wrap gap-1.5">
              {vendor.aliases.length === 0 && <span className="text-xs text-dim">none</span>}
              {vendor.aliases.map((a) => (
                <Badge key={a.id} tone="neutral">
                  {a.alias}
                  <button
                    type="button"
                    aria-label={`Remove alias ${a.alias}`}
                    className="text-dim hover:text-bad"
                    onClick={() => removeAlias.mutate({ id: vendor.id, aliasId: a.id }, { onError: (e) => toast.error(e.message) })}
                  >
                    <X className="size-3" />
                  </button>
                </Badge>
              ))}
            </div>
            <div className="flex gap-2">
              <Input id="v-alias" value={alias} maxLength={300} onChange={(e) => setAlias(e.target.value)} placeholder="Another name on its invoices" />
              <Button type="button" variant="outline" onClick={add} disabled={addAlias.isPending || !alias.trim()}>
                <Plus /> Add
              </Button>
            </div>
          </div>
          <div>
            <Label>Learned from corrections</Label>
            <p className="mb-2 text-xs text-dim">
              Plain rules taken from people&apos;s corrections and applied to this vendor&apos;s next invoices (marked there as
              &quot;from vendor memory&quot;). Not AI training: remove a rule and it stops.
            </p>
            {vendor.memory.length === 0 && <span className="text-xs text-dim">nothing yet</span>}
            <ul className="space-y-1.5">
              {vendor.memory.map((m) => (
                <li key={m.id} className="flex items-center justify-between gap-2 rounded-control border border-line px-3 py-2 text-sm">
                  <div className="min-w-0">
                    <div>{describeMemory(m)}</div>
                    <div className="font-mono text-[11px] text-dim">
                      {m.learnedByName ? `${m.learnedByName}, ` : ""}
                      {formatDate(m.learnedAt)} · used {m.timesApplied}×
                    </div>
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Forget: ${describeMemory(m)}`}
                    onClick={() => forget.mutate({ id: vendor.id, memoryId: m.id }, { onError: (e) => toast.error(e.message) })}
                  >
                    <X />
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={save} disabled={update.isPending || !name.trim()}>
            {update.isPending && <Loader2 className="animate-spin" />} Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default function VendorsPage() {
  const [filter, setFilter] = useState<"all" | "UNVERIFIED">("all");
  const { data, isLoading, error } = useVendors(filter === "all" ? undefined : filter);
  const verify = useVerifyVendor();
  const [editing, setEditing] = useState<Vendor | null>(null);

  return (
    <>
      <PageHeader
        title="Vendors"
        subtitle="Suppliers found on your invoices. New ones are created unverified; confirm each real supplier once."
      />
      <Tabs value={filter} onValueChange={(v) => setFilter(v as "all" | "UNVERIFIED")} className="mb-4">
        <TabsList>
          <TabsTrigger value="all">All</TabsTrigger>
          <TabsTrigger value="UNVERIFIED">To confirm</TabsTrigger>
        </TabsList>
      </Tabs>
      <Card className="overflow-hidden">
        {error ? (
          <p className="p-10 text-center text-sm text-bad">{error.message}</p>
        ) : (
          <Table>
            <THead>
              <TR>
                <TH>Vendor</TH>
                <TH>Tax id</TH>
                <TH>Currency</TH>
                <TH className="text-right">Invoices</TH>
                <TH>Status</TH>
                <TH className="sr-only">Actions</TH>
              </TR>
            </THead>
            <TBody>
              {isLoading &&
                Array.from({ length: 3 }).map((_, i) => (
                  <TR key={i}>
                    <TD colSpan={6}>
                      <Skeleton className="h-6" />
                    </TD>
                  </TR>
                ))}
              {data?.length === 0 && (
                <TR>
                  <TD colSpan={6} className="py-10 text-center text-sm text-muted">
                    {filter === "all" ? "No vendors yet: they appear with the first invoices." : "Every vendor is confirmed."}
                  </TD>
                </TR>
              )}
              {data?.map((v) => (
                <TR key={v.id}>
                  <TD>
                    <div className="font-medium">{v.name}</div>
                    {v.aliases.length > 0 && <div className="text-xs text-dim">also: {v.aliases.map((a) => a.alias).join(", ")}</div>}
                  </TD>
                  <TD className="font-mono text-xs">{v.taxId ?? "—"}</TD>
                  <TD className="font-mono text-xs">{v.defaultCurrency ?? "—"}</TD>
                  <TD className="text-right font-mono text-xs">{v.invoiceCount}</TD>
                  <TD>
                    {v.status === "VERIFIED" ? (
                      <Badge tone="ok" title={v.verifiedByName ? `by ${v.verifiedByName}${v.verifiedAt ? `, ${formatDate(v.verifiedAt)}` : ""}` : undefined}>
                        confirmed
                      </Badge>
                    ) : (
                      <Badge tone="info">to confirm</Badge>
                    )}
                  </TD>
                  <TD className="whitespace-nowrap text-right">
                    <Button variant="ghost" size="icon" aria-label={`Edit ${v.name}`} onClick={() => setEditing(v)}>
                      <Pencil />
                    </Button>
                    {v.status === "UNVERIFIED" && (
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={verify.isPending}
                        onClick={() => verify.mutate(v.id, { onSuccess: () => toast.success(`${v.name} confirmed`), onError: (e) => toast.error(e.message) })}
                      >
                        <BadgeCheck /> Confirm
                      </Button>
                    )}
                  </TD>
                </TR>
              ))}
            </TBody>
          </Table>
        )}
      </Card>
      {editing && <EditVendor vendor={data?.find((v) => v.id === editing.id) ?? editing} onClose={() => setEditing(null)} />}
    </>
  );
}
