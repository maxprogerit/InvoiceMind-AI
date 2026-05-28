"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { Button } from "@/components/ui/button";
import { SkeletonRow } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Modal } from "@/components/ui/modal";
import { Drawer } from "@/components/ui/drawer";
import { useNotificationStore } from "@/store/notification-store";
import { vendorsApi, Vendor } from "@/services/vendors-api";
import { Plus, Pencil, Trash2 } from "lucide-react";

function VendorForm({ initial, onSave, onClose }: {
  initial?: Partial<Vendor>;
  onSave: (data: Partial<Vendor>) => void;
  onClose: () => void;
}) {
  const [form, setForm] = useState<Partial<Vendor>>(initial ?? {});
  const set = (k: keyof Vendor) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));
  return (
    <div className="space-y-3">
      {(["name", "contactEmail", "contactPhone", "paymentTerms", "country"] as const).map((k) => (
        <div key={k}>
          <label className="mb-1 block text-xs text-slate-400 capitalize">{k}</label>
          <input value={(form[k] as string) ?? ""} onChange={set(k)}
            className="w-full rounded-xl border border-border/60 bg-slate-800 px-3 py-2 text-sm text-foreground" />
        </div>
      ))}
      <div className="flex gap-2 pt-2">
        <Button onClick={() => onSave(form)}>Save</Button>
        <Button onClick={onClose} className="bg-slate-700 text-slate-200">Cancel</Button>
      </div>
    </div>
  );
}

export default function VendorsPage() {
  const qc = useQueryClient();
  const { success, error } = useNotificationStore();
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<Vendor | null>(null);
  const [detailVendor, setDetailVendor] = useState<Vendor | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null);

  const { data: vendors = [], isLoading } = useQuery({
    queryKey: ["vendors"],
    queryFn: vendorsApi.list,
  });

  const createMutation = useMutation({
    mutationFn: (v: Partial<Vendor>) => vendorsApi.create(v),
    onSuccess: () => { success("Vendor created"); setCreateOpen(false); qc.invalidateQueries({ queryKey: ["vendors"] }); },
    onError: (e: Error) => error(e.message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<Vendor> }) => vendorsApi.update(id, data),
    onSuccess: () => { success("Vendor updated"); setEditTarget(null); qc.invalidateQueries({ queryKey: ["vendors"] }); },
    onError: (e: Error) => error(e.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => vendorsApi.delete(id),
    onSuccess: () => { success("Vendor deleted"); setDeleteTarget(null); qc.invalidateQueries({ queryKey: ["vendors"] }); },
    onError: (e: Error) => error(e.message),
  });

  return (
    <PageShell title="Vendors" subtitle="Vendor intelligence, payment history, risk scoring, and invoice linkage.">
      <div className="mb-4">
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="mr-2 h-4 w-4" /> Add Vendor
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">{Array.from({ length: 5 }).map((_, i) => <SkeletonRow key={i} />)}</div>
      ) : vendors.length === 0 ? (
        <EmptyState title="No vendors yet" description="Add your first vendor." />
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {vendors.map((v) => (
            <div key={v.id} className="rounded-xl border border-border/40 p-4">
              <div className="flex items-start justify-between">
                <div className="cursor-pointer" onClick={() => setDetailVendor(v)}>
                  <p className="font-semibold text-foreground">{v.name}</p>
                  <p className="text-xs text-slate-400">{v.contactEmail ?? "—"} • {v.country ?? "—"}</p>
                  <p className="text-xs text-slate-500 mt-0.5">Terms: {v.paymentTerms ?? "—"}</p>
                </div>
                <div className="flex gap-1">
                  <button onClick={() => setEditTarget(v)} className="rounded p-1 text-slate-400 hover:text-slate-200">
                    <Pencil className="h-4 w-4" />
                  </button>
                  <button onClick={() => setDeleteTarget(v.id)} className="rounded p-1 text-slate-400 hover:text-rose-400">
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Create Vendor">
        <VendorForm onSave={(data) => createMutation.mutate(data)} onClose={() => setCreateOpen(false)} />
      </Modal>

      <Modal open={!!editTarget} onClose={() => setEditTarget(null)} title="Edit Vendor">
        {editTarget && (
          <VendorForm initial={editTarget}
            onSave={(data) => updateMutation.mutate({ id: editTarget.id, data })}
            onClose={() => setEditTarget(null)} />
        )}
      </Modal>

      <Drawer open={!!detailVendor} onClose={() => setDetailVendor(null)} title="Vendor Details">
        {detailVendor && (
          <div className="space-y-3 text-sm">
            <div><span className="text-slate-500">Name:</span><p className="text-foreground">{detailVendor.name}</p></div>
            <div><span className="text-slate-500">Email:</span><p className="text-foreground">{detailVendor.contactEmail ?? "—"}</p></div>
            <div><span className="text-slate-500">Phone:</span><p className="text-foreground">{detailVendor.contactPhone ?? "—"}</p></div>
            <div><span className="text-slate-500">Country:</span><p className="text-foreground">{detailVendor.country ?? "—"}</p></div>
            <div><span className="text-slate-500">Payment Terms:</span><p className="text-foreground">{detailVendor.paymentTerms ?? "—"}</p></div>
          </div>
        )}
      </Drawer>

      <Modal open={deleteTarget !== null} onClose={() => setDeleteTarget(null)} title="Delete Vendor">
        <p className="mb-4 text-sm text-slate-300">Are you sure you want to delete this vendor?</p>
        <div className="flex gap-2">
          <Button onClick={() => deleteMutation.mutate(deleteTarget!)}
            className="border-rose-400/40 bg-rose-500/15 text-rose-300">Delete</Button>
          <Button onClick={() => setDeleteTarget(null)} className="bg-slate-700 text-slate-200">Cancel</Button>
        </div>
      </Modal>
    </PageShell>
  );
}

