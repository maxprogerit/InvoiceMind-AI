"use client";

import { Check, ChevronDown, Copy, Loader2, Plus, RotateCw, Send, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { toast } from "sonner";

import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/misc";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import {
  useCreateWebhook,
  useDeleteWebhook,
  useRetryDelivery,
  useTestWebhook,
  useUpdateWebhook,
  useWebhookDeliveries,
  useWebhookEvents,
  useWebhooks,
} from "@/lib/api/hooks";
import type { WebhookEndpoint } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/store";
import { cn } from "@/lib/utils";

const EVENT_HINT: Record<string, string> = {
  "invoice.validated": "Passed every check automatically",
  "invoice.needs_review": "Needs a human look",
  "invoice.approved": "Approved: the one an ERP usually wants",
  "invoice.rejected": "Rejected by an approver",
  "invoice.failed": "Could not be processed",
};

const time = (iso?: string) => (iso ? new Date(iso).toLocaleString() : "—");

const VERIFY_SNIPPET = `// Node.js: verify the signature of an incoming InvoiceMind webhook
import crypto from "node:crypto";

function verify(rawBody, header, secret, toleranceSeconds = 300) {
  const [t, v1] = header.split(",").map((p) => p.split("=")[1]);   // "t=...,v1=..."
  if (Math.abs(Date.now() / 1000 - Number(t)) > toleranceSeconds) return false;   // replay protection
  const expected = crypto.createHmac("sha256", secret).update(t + "." + rawBody).digest("hex");
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(v1));
}
// Deduplicate on the "id" field of the event: delivery is at-least-once.`;

function Deliveries({ endpoint }: { endpoint: WebhookEndpoint }) {
  const { data, isLoading } = useWebhookDeliveries(endpoint.id, true);
  const retry = useRetryDelivery(endpoint.id);
  return (
    <div className="mt-4 overflow-hidden rounded-control border border-line">
      <Table>
        <THead>
          <TR>
            <TH>Event</TH>
            <TH>Status</TH>
            <TH>Attempts</TH>
            <TH>Last answer</TH>
            <TH>Created</TH>
            <TH className="text-right">
              <span className="sr-only">Actions</span>
            </TH>
          </TR>
        </THead>
        <TBody>
          {isLoading && (
            <TR>
              <TD colSpan={6}>
                <Skeleton className="h-6" />
              </TD>
            </TR>
          )}
          {data?.length === 0 && (
            <TR>
              <TD colSpan={6} className="py-6 text-center text-sm text-dim">
                Nothing has been sent yet. Use “Send test”.
              </TD>
            </TR>
          )}
          {data?.map((d) => (
            <TR key={d.id}>
              <TD className="font-mono text-xs">{d.eventType}</TD>
              <TD>
                <Badge tone={d.status === "DELIVERED" ? "ok" : d.status === "FAILED" ? "bad" : "warn"}>{d.status.toLowerCase()}</Badge>
              </TD>
              <TD className="num">{d.attempts}</TD>
              <TD className="max-w-[260px] truncate text-xs text-muted" title={d.lastError ?? undefined}>
                {d.lastStatusCode ? `HTTP ${d.lastStatusCode}` : (d.lastError ?? "—")}
              </TD>
              <TD className="whitespace-nowrap font-mono text-xs text-muted">{time(d.createdAt)}</TD>
              <TD className="text-right">
                {d.status === "FAILED" && (
                  <Button size="sm" variant="outline" disabled={retry.isPending} onClick={() => retry.mutate(d.id, { onError: (e) => toast.error(e.message) })}>
                    <RotateCw /> Retry
                  </Button>
                )}
              </TD>
            </TR>
          ))}
        </TBody>
      </Table>
    </div>
  );
}

function EndpointCard({ endpoint }: { endpoint: WebhookEndpoint }) {
  const [open, setOpen] = useState(false);
  const update = useUpdateWebhook();
  const remove = useDeleteWebhook();
  const test = useTestWebhook(endpoint.id);
  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="truncate font-mono text-sm">{endpoint.url}</span>
            <Badge tone={endpoint.enabled ? "ok" : "neutral"}>{endpoint.enabled ? "enabled" : "disabled"}</Badge>
          </div>
          {endpoint.description && <p className="mt-1 text-sm text-muted">{endpoint.description}</p>}
          <div className="mt-2 flex flex-wrap gap-1.5">
            {endpoint.events.map((e) => (
              <Badge key={e} tone="accent">
                {e}
              </Badge>
            ))}
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            size="sm"
            variant="outline"
            disabled={test.isPending || !endpoint.enabled}
            onClick={() => {
              test.mutate(undefined, { onSuccess: () => { toast.success("Test event queued"); setOpen(true); }, onError: (e) => toast.error(e.message) });
            }}
          >
            <Send /> Send test
          </Button>
          <Button size="sm" variant="outline" onClick={() => update.mutate({ id: endpoint.id, enabled: !endpoint.enabled }, { onError: (e) => toast.error(e.message) })}>
            {endpoint.enabled ? "Disable" : "Enable"}
          </Button>
          <Button size="sm" variant="ghost" aria-label={`Delete the webhook for ${endpoint.url}`} onClick={() => remove.mutate(endpoint.id, { onSuccess: () => toast.success("Webhook deleted"), onError: (e) => toast.error(e.message) })}>
            <Trash2 />
          </Button>
        </div>
      </div>
      <button type="button" onClick={() => setOpen((o) => !o)} className="mt-4 flex items-center gap-1.5 font-mono text-xs text-dim hover:text-muted" aria-expanded={open}>
        <ChevronDown className={cn("size-3.5 transition-transform", open && "rotate-180")} /> Deliveries
      </button>
      {open && <Deliveries endpoint={endpoint} />}
    </Card>
  );
}

function SecretDialog({ secret, onClose }: { secret: string | null; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  return (
    <Dialog open={!!secret} onOpenChange={(o) => !o && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Signing secret</DialogTitle>
          <DialogDescription>Copy it now. It is shown only once; every request is signed with it so your system can verify where it came from.</DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-2 rounded-control border border-line bg-bg/60 p-3">
          <code className="min-w-0 flex-1 break-all font-mono text-xs" data-testid="webhook-secret">
            {secret}
          </code>
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              void navigator.clipboard?.writeText(secret ?? "").then(() => setCopied(true));
            }}
          >
            {copied ? <Check /> : <Copy />} {copied ? "Copied" : "Copy"}
          </Button>
        </div>
        <DialogFooter>
          <Button onClick={onClose}>Done</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function AddWebhook({ open, onOpenChange, onCreated }: { open: boolean; onOpenChange: (o: boolean) => void; onCreated: (secret: string) => void }) {
  const events = useWebhookEvents();
  const create = useCreateWebhook();
  const [url, setUrl] = useState("");
  const [description, setDescription] = useState("");
  const [selected, setSelected] = useState<string[]>(["invoice.approved"]);
  const toggle = (e: string) => setSelected((s) => (s.includes(e) ? s.filter((x) => x !== e) : [...s, e]));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a webhook</DialogTitle>
          <DialogDescription>InvoiceMind will POST a signed JSON event to this URL. Private and internal addresses are refused unless the server allows them.</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            create.mutate(
              { url: url.trim(), description: description.trim() || undefined, events: selected },
              {
                onSuccess: (created) => {
                  setUrl("");
                  setDescription("");
                  onOpenChange(false);
                  onCreated(created.secret);
                },
                onError: (err) => toast.error(err.message),
              },
            );
          }}
        >
          <div>
            <Label htmlFor="wh-url">Endpoint URL</Label>
            <Input id="wh-url" type="url" required placeholder="https://erp.example.com/hooks/invoicemind" value={url} onChange={(e) => setUrl(e.target.value)} />
          </div>
          <div>
            <Label htmlFor="wh-desc">Description (optional)</Label>
            <Input id="wh-desc" placeholder="SAP posting service" value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>
          <fieldset>
            <legend className="mb-1.5 text-sm font-medium">Events</legend>
            <div className="space-y-1.5">
              {(events.data ?? Object.keys(EVENT_HINT)).map((e) => (
                <label key={e} className="flex cursor-pointer items-start gap-2.5 rounded-md px-2 py-1.5 hover:bg-surface-2">
                  <input type="checkbox" className="mt-1 accent-[var(--color-accent)]" checked={selected.includes(e)} onChange={() => toggle(e)} />
                  <span>
                    <span className="font-mono text-[13px]">{e}</span>
                    <span className="block text-xs text-dim">{EVENT_HINT[e]}</span>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={create.isPending || selected.length === 0 || !url.trim()}>
              {create.isPending && <Loader2 className="animate-spin" />} Add webhook
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export default function IntegrationsPage() {
  const router = useRouter();
  const me = useAuth((s) => s.user);
  const admin = me?.role === "ADMIN";
  useEffect(() => {
    if (me && !admin) router.replace("/invoices");
  }, [me, admin, router]);

  const { data, isLoading, error } = useWebhooks(admin);
  const [adding, setAdding] = useState(false);
  const [secret, setSecret] = useState<string | null>(null);
  if (!admin) return null;

  return (
    <>
      <PageHeader
        title="Integrations"
        subtitle="Push approved invoices into your ERP or accounting system the moment they are decided."
        actions={
          <Button onClick={() => setAdding(true)}>
            <Plus /> Add webhook
          </Button>
        }
      />
      {error ? (
        <p className="text-sm text-bad">{error.message}</p>
      ) : (
        <div className="space-y-4">
          {isLoading && <Skeleton className="h-28" />}
          {data?.length === 0 && (
            <Card className="p-10 text-center text-sm text-muted">
              No webhooks yet. Add one and InvoiceMind sends a signed JSON event whenever an invoice is approved, rejected, flagged or fails.
            </Card>
          )}
          {data?.map((w) => <EndpointCard key={w.id} endpoint={w} />)}
        </div>
      )}

      <Card className="mt-6 p-6">
        <div className="label-mono">Verifying a request</div>
        <p className="mt-3 max-w-2xl text-sm text-muted">
          Each request carries <code className="font-mono text-xs text-fg">X-InvoiceMind-Signature: t=&lt;unix time&gt;,v1=&lt;hex&gt;</code>, an HMAC-SHA256 of the timestamp, a dot and the raw body, keyed with the signing secret. Redirects are never followed, and failed
          deliveries are retried with exponential backoff for about an hour.
        </p>
        <pre className="mt-4 overflow-x-auto rounded-control border border-line bg-bg/60 p-4 font-mono text-xs leading-relaxed">{VERIFY_SNIPPET}</pre>
      </Card>

      <AddWebhook open={adding} onOpenChange={setAdding} onCreated={setSecret} />
      <SecretDialog secret={secret} onClose={() => setSecret(null)} />
    </>
  );
}
