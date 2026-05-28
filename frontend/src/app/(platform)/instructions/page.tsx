"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { Button } from "@/components/ui/button";
import { SkeletonRow } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Modal } from "@/components/ui/modal";
import { ConfirmModal } from "@/components/ui/modal";
import { useNotificationStore } from "@/store/notification-store";
import { instructionsApi, InstructionRule } from "@/services/instructions-api";
import { Plus, Pencil, Trash2 } from "lucide-react";

const RULE_TYPES = ["OCR_EXTRACTION", "VENDOR_MATCHING", "APPROVAL_RULE", "AUTOMATION_RULE"];

function RuleForm({ initial, onSave, onClose }: {
  initial?: Partial<InstructionRule>;
  onSave: (d: Partial<InstructionRule>) => void;
  onClose: () => void;
}) {
  const [form, setForm] = useState<Partial<InstructionRule>>(initial ?? { active: true });
  return (
    <div className="space-y-3">
      <div>
        <label className="mb-1 block text-xs text-slate-400">Name</label>
        <input value={form.name ?? ""} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          className="w-full rounded-xl border border-border/60 bg-slate-800 px-3 py-2 text-sm text-foreground" />
      </div>
      <div>
        <label className="mb-1 block text-xs text-slate-400">Rule Type</label>
        <select value={form.ruleType ?? RULE_TYPES[0]} onChange={(e) => setForm((f) => ({ ...f, ruleType: e.target.value }))}
          className="w-full rounded-xl border border-border/60 bg-slate-800 px-3 py-2 text-sm text-foreground">
          {RULE_TYPES.map((t) => <option key={t}>{t}</option>)}
        </select>
      </div>
      <div>
        <label className="mb-1 block text-xs text-slate-400">Pattern / Condition</label>
        <input value={form.pattern ?? ""} onChange={(e) => setForm((f) => ({ ...f, pattern: e.target.value }))}
          placeholder="e.g. vendor:contains:Acme"
          className="w-full rounded-xl border border-border/60 bg-slate-800 px-3 py-2 text-sm text-foreground" />
      </div>
      <div>
        <label className="mb-1 block text-xs text-slate-400">Action / Config</label>
        <input value={form.actionConfig ?? ""} onChange={(e) => setForm((f) => ({ ...f, actionConfig: e.target.value }))}
          placeholder="e.g. auto_approve"
          className="w-full rounded-xl border border-border/60 bg-slate-800 px-3 py-2 text-sm text-foreground" />
      </div>
      <div className="flex gap-2 pt-2">
        <Button onClick={() => onSave(form)}>Save Rule</Button>
        <Button onClick={onClose} className="bg-slate-700 text-slate-200">Cancel</Button>
      </div>
    </div>
  );
}

export default function InstructionsPage() {
  const qc = useQueryClient();
  const { success, error } = useNotificationStore();
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<InstructionRule | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null);

  const { data: rules = [], isLoading } = useQuery({
    queryKey: ["instructions"],
    queryFn: instructionsApi.list,
  });

  const createMutation = useMutation({
    mutationFn: (d: Partial<InstructionRule>) => instructionsApi.create(d),
    onSuccess: () => { success("Rule created"); setCreateOpen(false); qc.invalidateQueries({ queryKey: ["instructions"] }); },
    onError: (e: Error) => error(e.message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: Partial<InstructionRule> }) => instructionsApi.update(id, data),
    onSuccess: () => { success("Rule updated"); setEditTarget(null); qc.invalidateQueries({ queryKey: ["instructions"] }); },
    onError: (e: Error) => error(e.message),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => instructionsApi.delete(id),
    onSuccess: () => { success("Rule deleted"); setDeleteTarget(null); qc.invalidateQueries({ queryKey: ["instructions"] }); },
    onError: (e: Error) => error(e.message),
  });

  return (
    <PageShell title="Instructions" subtitle="Define extraction prompts, validation logic, and business automation rules.">
      <div className="mb-4">
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="mr-2 h-4 w-4" /> Add Rule
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">{Array.from({ length: 4 }).map((_, i) => <SkeletonRow key={i} />)}</div>
      ) : rules.length === 0 ? (
        <EmptyState title="No rules yet" description="Add your first AI instruction rule." />
      ) : (
        <div className="space-y-3">
          {rules.map((r: InstructionRule) => (
            <div key={r.id} className="flex items-start justify-between rounded-xl border border-border/40 p-4">
              <div>
                <p className="font-medium text-foreground">{r.name}</p>
                <p className="text-xs text-slate-400">{r.ruleType} • Pattern: {r.pattern ?? "—"} → {r.actionConfig ?? "—"}</p>
              </div>
              <div className="flex items-center gap-3">
                <span className={`text-xs font-medium ${r.active ? "text-emerald-400" : "text-slate-500"}`}>
                  {r.active ? "Active" : "Inactive"}
                </span>
                <button onClick={() => setEditTarget(r)} className="rounded p-1 text-slate-400 hover:text-slate-200">
                  <Pencil className="h-4 w-4" />
                </button>
                <button onClick={() => setDeleteTarget(r.id)} className="rounded p-1 text-slate-400 hover:text-rose-400">
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Create Rule">
        <RuleForm onSave={(d) => createMutation.mutate(d)} onClose={() => setCreateOpen(false)} />
      </Modal>
      <Modal open={!!editTarget} onClose={() => setEditTarget(null)} title="Edit Rule">
        {editTarget && (
          <RuleForm initial={editTarget}
            onSave={(d) => updateMutation.mutate({ id: editTarget.id, data: d })}
            onClose={() => setEditTarget(null)} />
        )}
      </Modal>
      <ConfirmModal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget !== null && deleteMutation.mutate(deleteTarget)}
        title="Delete Rule"
        message="Are you sure you want to delete this instruction rule?"
        confirmLabel="Delete"
        danger
      />
    </PageShell>
  );
}

