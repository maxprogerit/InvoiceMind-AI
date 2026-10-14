"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Check, Lock, Plus, RefreshCw, Save, Sparkles, Trash2 } from "lucide-react";
import { useEffect, useMemo, useRef } from "react";
import { useFieldArray, useForm, useWatch, type FieldPath } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Card, CardLabel } from "@/components/ui/card";
import { Input, Label, fieldClass } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { useUpdateInvoice } from "@/lib/api/hooks";
import type { InvoiceDetail, UpdateInvoiceRequest } from "@/lib/api/types";
import { formatAmount } from "@/lib/format";
import { isConsistent, parseAmount as parse, sumChecks } from "@/lib/sums";
import { cn } from "@/lib/utils";

// ------------------------------------------------------------------ schema
const money = z.string().refine((v) => v.trim() === "" || /^-?\d{1,12}(\.\d{1,2})?$/.test(v.trim()), "Up to 2 decimals");
const quantity = z.string().refine((v) => v.trim() === "" || /^-?\d{1,10}(\.\d{1,4})?$/.test(v.trim()), "Number, up to 4 decimals");
const isoDate = z.string().refine((v) => v === "" || /^\d{4}-\d{2}-\d{2}$/.test(v), "Use YYYY-MM-DD");

const schema = z.object({
  documentType: z.enum(["INVOICE", "CREDIT_NOTE"]),
  vendorName: z.string().max(300),
  invoiceNumber: z.string().max(100),
  invoiceDate: isoDate,
  dueDate: isoDate,
  currency: z.string().max(3),
  subtotal: money,
  tax: money,
  total: money,
  lineItems: z
    .array(z.object({ description: z.string().trim().min(1, "Required").max(1000), quantity, unitPrice: quantity, total: money }))
    .max(500),
});
type FormValues = z.infer<typeof schema>;

const money2 = (n?: number | null) => (n == null ? "" : n.toFixed(2));
const plain = (n?: number | null) => (n == null ? "" : String(Number(n.toFixed(4))));

function toForm(inv: InvoiceDetail): FormValues {
  return {
    documentType: inv.documentType,
    vendorName: inv.vendorName ?? "",
    invoiceNumber: inv.invoiceNumber ?? "",
    invoiceDate: inv.invoiceDate ?? "",
    dueDate: inv.dueDate ?? "",
    currency: inv.currency ?? "",
    subtotal: money2(inv.subtotal),
    tax: money2(inv.tax),
    total: money2(inv.total),
    lineItems: inv.lineItems.map((l) => ({
      description: l.description,
      quantity: plain(l.quantity),
      unitPrice: plain(l.unitPrice),
      total: money2(l.total),
    })),
  };
}

const num = (v: string | undefined) => (v === undefined || v.trim() === "" ? undefined : Number(v));

function toRequest(v: FormValues, version: number): UpdateInvoiceRequest {
  return {
    documentType: v.documentType,
    vendorName: v.vendorName.trim() || undefined,
    invoiceNumber: v.invoiceNumber.trim() || undefined,
    invoiceDate: v.invoiceDate || undefined,
    dueDate: v.dueDate || undefined,
    currency: v.currency.trim().toUpperCase() || undefined,
    subtotal: num(v.subtotal),
    tax: num(v.tax),
    total: num(v.total),
    lineItems: v.lineItems.map((l) => ({ description: l.description.trim(), quantity: num(l.quantity), unitPrice: num(l.unitPrice), total: num(l.total) })),
    version,
  };
}

// FormValues field -> key used in the AI confidence map
const CONFIDENCE_KEY: Partial<Record<keyof FormValues, string>> = {
  vendorName: "vendor",
  invoiceNumber: "invoiceNumber",
  invoiceDate: "invoiceDate",
  dueDate: "dueDate",
  currency: "currency",
  subtotal: "subtotal",
  tax: "tax",
  total: "total",
};

const CURRENCIES = ["EUR", "USD", "GBP", "CHF", "PLN", "CZK", "SEK", "NOK", "DKK", "UAH", "TRY", "JPY", "CNY", "CAD", "AUD"];

// -------------------------------------------------------------- component
export function ReviewForm({ invoice }: { invoice: InvoiceDetail }) {
  const editable = invoice.permissions.canEdit;
  const qc = useQueryClient();
  const update = useUpdateInvoice(invoice.id);
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: toForm(invoice) });
  const { fields, append, remove } = useFieldArray({ control: form.control, name: "lineItems" });
  const values = useWatch({ control: form.control });
  const documentType = values.documentType ?? invoice.documentType;
  const { errors, isDirty } = form.formState;

  // Re-sync with the server when the invoice changes underneath us (live event, other user),
  // but never overwrite edits the user is typing.
  const syncedVersion = useRef(invoice.version);
  const stale = invoice.version !== syncedVersion.current && isDirty;
  useEffect(() => {
    if (invoice.version !== syncedVersion.current && !form.formState.isDirty) {
      form.reset(toForm(invoice));
      syncedVersion.current = invoice.version;
    }
  }, [invoice, form]);

  const issuesFor = useMemo(() => {
    const map = new Map<string, "ERROR" | "WARNING">();
    for (const i of invoice.issues) {
      if (!i.field) continue;
      if (map.get(i.field) !== "ERROR") map.set(i.field, i.severity as "ERROR" | "WARNING");
    }
    return map;
  }, [invoice.issues]);

  const checks = useMemo(() => sumChecks(values as Partial<FormValues>), [values]);

  function onSubmit(v: FormValues) {
    update.mutate(toRequest(v, invoice.version), {
      onSuccess: (saved) => {
        form.reset(toForm(saved));
        syncedVersion.current = saved.version;
        if (saved.status === "VALIDATED") toast.success("Saved. All checks passed, ready for approval.");
        else toast.warning("Saved, but some checks still fail. See the validation panel.");
      },
      onError: (e) => {
        if (e instanceof ApiError && e.code === "stale_version") {
          toast.error("Someone else changed this invoice. Reloaded the latest version.");
          void qc.invalidateQueries({ queryKey: ["invoice", invoice.id] });
        } else if (e instanceof ApiError && e.fieldErrors) {
          for (const [path, message] of Object.entries(e.fieldErrors)) form.setError(path as FieldPath<FormValues>, { message });
          toast.error("Please fix the highlighted fields.");
        } else {
          toast.error(e.message);
        }
      },
    });
  }

  // A labelled field with confidence chip and validation highlight. A plain render function (not a nested
  // component) so inputs keep their identity - and focus - across re-renders.
  const renderField = ({ name, label, type = "text", mono, inputMode, placeholder, listId }: {
    name: Exclude<keyof FormValues, "lineItems" | "documentType">;
    label: string;
    type?: string;
    mono?: boolean;
    inputMode?: "decimal";
    placeholder?: string;
    listId?: string;
  }) => {
    const issue = issuesFor.get(name);
    const confidence = invoice.fieldConfidence?.[CONFIDENCE_KEY[name] ?? ""];
    const lowConfidence = !invoice.humanReviewed && confidence != null && confidence < 0.6;
    const tone = issue === "ERROR" ? "bad" : issue === "WARNING" || lowConfidence ? "warn" : null;
    const error = errors[name]?.message;
    return (
      <div>
        <div className="mb-1.5 flex items-center justify-between gap-2">
          <Label htmlFor={`f-${name}`} className="mb-0">
            {label}
          </Label>
          {confidence != null && !invoice.humanReviewed && (
            <span
              className={cn("flex items-center gap-1 font-mono text-[10px]", lowConfidence ? "text-warn" : "text-dim")}
              title="How sure the AI was about this value"
            >
              <Sparkles className="size-2.5" /> {Math.round(confidence * 100)}%
            </span>
          )}
        </div>
        <Input
          id={`f-${name}`}
          type={type}
          inputMode={inputMode}
          placeholder={placeholder}
          list={listId}
          disabled={!editable}
          aria-invalid={!!error || issue === "ERROR"}
          className={cn(mono && "font-mono", tone === "warn" && "border-warn/60 ring-1 ring-warn/20", tone === "bad" && "border-bad/70 ring-1 ring-bad/20")}
          {...form.register(name, name === "currency" ? { onChange: (e) => form.setValue("currency", e.target.value.toUpperCase().slice(0, 3), { shouldDirty: true }) } : undefined)}
        />
        {error && <p className="mt-1 text-xs text-bad">{error}</p>}
      </div>
    );
  };

  const cell = (i: number, key: "quantity" | "unitPrice" | "total" | "description") => {
    const msg = errors.lineItems?.[i]?.[key]?.message;
    const lineIssue = invoice.issues.find((x) => x.field === `lineItems[${i}]`);
    return { msg, lineIssue };
  };

  return (
    <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <Card className="p-6">
        <div className="flex items-center justify-between">
          <CardLabel>Extracted data</CardLabel>
          {!editable && (
            <span className="flex items-center gap-1.5 font-mono text-[11px] text-dim">
              <Lock className="size-3" /> read-only
            </span>
          )}
        </div>

        {stale && (
          <div className="mt-4 flex items-center justify-between gap-3 rounded-control border border-info/30 bg-info/10 px-3 py-2 text-sm text-info">
            <span>This invoice was updated elsewhere.</span>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => {
                form.reset(toForm(invoice));
                syncedVersion.current = invoice.version;
              }}
            >
              <RefreshCw /> Reload
            </Button>
          </div>
        )}

        <fieldset disabled={!editable} className="mt-5 space-y-4">
          <div>
            <Label className="mb-1.5">Document type</Label>
            <div role="radiogroup" aria-label="Document type" className="inline-flex rounded-control border border-line bg-bg/60 p-0.5">
              {(["INVOICE", "CREDIT_NOTE"] as const).map((t) => (
                <button
                  key={t}
                  type="button"
                  role="radio"
                  aria-checked={documentType === t}
                  onClick={() => form.setValue("documentType", t, { shouldDirty: true })}
                  className={cn(
                    "rounded-[8px] px-3 py-1.5 text-sm transition-colors disabled:cursor-not-allowed",
                    documentType === t ? "bg-surface-3 text-fg" : "text-dim hover:text-muted",
                  )}
                >
                  {t === "INVOICE" ? "Invoice" : "Credit note"}
                </button>
              ))}
            </div>
            {documentType === "CREDIT_NOTE" && (
              <p className="mt-1.5 text-xs text-dim">A credit note reduces what is owed, so its amounts are stored as negative values.</p>
            )}
          </div>
          {renderField({ name: "vendorName", label: "Vendor" })}
          <div className="grid gap-4 sm:grid-cols-2">
            {renderField({ name: "invoiceNumber", label: documentType === "CREDIT_NOTE" ? "Credit note number" : "Invoice number", mono: true })}
            {renderField({ name: "currency", label: "Currency", mono: true, placeholder: "EUR", listId: "currencies" })}
            {renderField({ name: "invoiceDate", label: "Invoice date", type: "date", mono: true })}
            {renderField({ name: "dueDate", label: "Due date", type: "date", mono: true })}
          </div>
          <datalist id="currencies">
            {CURRENCIES.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
          <div className="grid gap-4 sm:grid-cols-3">
            {renderField({ name: "subtotal", label: "Subtotal", inputMode: "decimal", mono: true })}
            {renderField({ name: "tax", label: "Tax", inputMode: "decimal", mono: true })}
            {renderField({ name: "total", label: "Total", inputMode: "decimal", mono: true })}
          </div>
        </fieldset>

        {checks.length > 0 && (
          <ul className="mt-4 space-y-1.5 rounded-control border border-line bg-bg/40 p-3" aria-label="Live arithmetic check">
            {checks.map((c) => {
              const ok = isConsistent(c);
              return (
                <li key={c.label} className="flex items-center justify-between gap-3 font-mono text-xs">
                  <span className="flex items-center gap-2 text-muted">
                    {ok ? <Check className="size-3.5 text-ok" /> : <AlertTriangle className="size-3.5 text-bad" />}
                    {c.label}
                  </span>
                  <span className={cn("num", ok ? "text-ok" : "text-bad")}>
                    {ok ? formatAmount(c.actual) : `${formatAmount(c.expected)} ≠ ${formatAmount(c.actual)}`}
                  </span>
                </li>
              );
            })}
          </ul>
        )}

        {/* ---------------------------------------------------------- line items */}
        <div className="mt-7">
          <div className="mb-2 flex items-center justify-between">
            <span className="label-mono">Line items ({fields.length})</span>
            {editable && (
              <Button type="button" size="sm" variant="outline" onClick={() => append({ description: "", quantity: "1", unitPrice: "", total: "" })}>
                <Plus /> Add line
              </Button>
            )}
          </div>
          {fields.length === 0 ? (
            <p className="rounded-control border border-dashed border-line-strong px-4 py-6 text-center text-sm text-dim">
              No line items were found on this document.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <div className="min-w-[560px] space-y-2">
                <div className="grid grid-cols-[1fr_72px_96px_104px_32px] gap-2 px-1">
                  {["Description", "Qty", "Unit price", "Total", ""].map((h) => (
                    <span key={h} className="label-mono text-[10px]">
                      {h}
                    </span>
                  ))}
                </div>
                {fields.map((f, i) => {
                  const { lineIssue } = cell(i, "total");
                  const q = parse(values.lineItems?.[i]?.quantity);
                  const u = parse(values.lineItems?.[i]?.unitPrice);
                  const t = parse(values.lineItems?.[i]?.total);
                  const mismatch = q !== null && u !== null && t !== null && Math.abs(q * u - t) > 0.0105;
                  const cls = (key: "quantity" | "unitPrice" | "total" | "description") =>
                    cn("h-9 px-2 text-[13px]", key !== "description" && "font-mono", (cell(i, key).msg || (lineIssue && key === "total")) && "border-bad/70", mismatch && key === "total" && "border-warn/60");
                  return (
                    <div key={f.id}>
                      <div className="grid grid-cols-[1fr_72px_96px_104px_32px] items-center gap-2">
                        <Input aria-label={`Line ${i + 1} description`} className={cls("description")} disabled={!editable} {...form.register(`lineItems.${i}.description`)} />
                        <Input aria-label={`Line ${i + 1} quantity`} inputMode="decimal" className={cls("quantity")} disabled={!editable} {...form.register(`lineItems.${i}.quantity`)} />
                        <Input aria-label={`Line ${i + 1} unit price`} inputMode="decimal" className={cls("unitPrice")} disabled={!editable} {...form.register(`lineItems.${i}.unitPrice`)} />
                        <Input aria-label={`Line ${i + 1} total`} inputMode="decimal" className={cls("total")} disabled={!editable} {...form.register(`lineItems.${i}.total`)} />
                        {editable ? (
                          <button
                            type="button"
                            onClick={() => remove(i)}
                            aria-label={`Remove line ${i + 1}`}
                            className="grid size-8 place-items-center rounded-md text-dim hover:bg-bad/10 hover:text-bad"
                          >
                            <Trash2 className="size-4" />
                          </button>
                        ) : (
                          <span />
                        )}
                      </div>
                      {mismatch && (
                        <p className="mt-1 pl-1 font-mono text-[10px] text-warn">
                          {q} × {formatAmount(u)} = {formatAmount(q! * u!)}, but the line total is {formatAmount(t)}
                        </p>
                      )}
                      {errors.lineItems?.[i]?.description?.message && <p className="mt-1 pl-1 text-xs text-bad">{errors.lineItems[i]?.description?.message}</p>}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {editable && (
          <div className="mt-7 flex flex-wrap items-center justify-between gap-3 border-t border-line pt-5">
            <p className="max-w-sm text-xs leading-relaxed text-dim">
              Saving re-runs every check on your values. Your edit is recorded in the audit log with before and after.
            </p>
            <div className="flex gap-2">
              <Button type="button" variant="ghost" disabled={!isDirty || update.isPending} onClick={() => form.reset(toForm(invoice))}>
                Discard
              </Button>
              <Button type="submit" disabled={!isDirty || update.isPending}>
                <Save /> {update.isPending ? "Saving…" : "Save & revalidate"}
              </Button>
            </div>
          </div>
        )}
      </Card>
    </form>
  );
}

export { fieldClass };
