"use client";

import { useState, useCallback } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import ReactFlow, {
  Controls, Background, MiniMap, Node, Edge,
  addEdge, useNodesState, useEdgesState, Connection
} from "reactflow";
import "reactflow/dist/style.css";
import { PageShell } from "@/components/platform/page-shell";
import { Button } from "@/components/ui/button";
import { SkeletonRow } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { statusBadge } from "@/components/ui/badge";
import { useNotificationStore } from "@/store/notification-store";
import { workflowsApi, Workflow } from "@/services/workflows-api";
import { Plus, Save, Play } from "lucide-react";

const DEFAULT_NODES: Node[] = [
  { id: "1", position: { x: 0, y: 50 }, data: { label: "Upload Document" }, type: "input" },
  { id: "2", position: { x: 250, y: 50 }, data: { label: "AI OCR Node" } },
  { id: "3", position: { x: 500, y: 50 }, data: { label: "Validation Node" } },
  { id: "4", position: { x: 750, y: 50 }, data: { label: "Approval Node" } },
  { id: "5", position: { x: 1000, y: 50 }, data: { label: "ERP Export Node" }, type: "output" },
];
const DEFAULT_EDGES: Edge[] = [
  { id: "e1-2", source: "1", target: "2", animated: true },
  { id: "e2-3", source: "2", target: "3", animated: true },
  { id: "e3-4", source: "3", target: "4", animated: true },
  { id: "e4-5", source: "4", target: "5", animated: true },
];

export default function WorkflowsPage() {
  const qc = useQueryClient();
  const { success, error } = useNotificationStore();
  const [selected, setSelected] = useState<Workflow | null>(null);
  const [nodes, setNodes, onNodesChange] = useNodesState(DEFAULT_NODES);
  const [edges, setEdges, onEdgesChange] = useEdgesState(DEFAULT_EDGES);
  const onConnect = useCallback((c: Connection) => setEdges((e) => addEdge(c, e)), []);

  const { data: workflows = [], isLoading } = useQuery({
    queryKey: ["workflows"],
    queryFn: workflowsApi.list,
  });

  const saveMutation = useMutation({
    mutationFn: () => {
      const definitionJson = JSON.stringify({ nodes, edges });
      if (selected) return workflowsApi.update(selected.id, { definitionJson });
      return workflowsApi.create({ name: "New Workflow", definitionJson });
    },
    onSuccess: (w) => {
      success("Workflow saved");
      setSelected(w);
      qc.invalidateQueries({ queryKey: ["workflows"] });
    },
    onError: (e: Error) => error(e.message),
  });

  const executeMutation = useMutation({
    mutationFn: (id: number) => workflowsApi.execute(id),
    onSuccess: () => {
      success("Workflow execution started");
      qc.invalidateQueries({ queryKey: ["executions"] });
    },
    onError: (e: Error) => error(e.message),
  });

  const loadWorkflow = (w: Workflow) => {
    setSelected(w);
    try {
      const def = JSON.parse(w.definitionJson ?? w.definition ?? "{}");
      if (def.nodes) setNodes(def.nodes);
      if (def.edges) setEdges(def.edges);
    } catch { /* keep default */ }
  };

  return (
    <PageShell title="Workflows" subtitle="Visual drag-and-drop workflow builder with AI OCR, validation, approvals, and exports.">
      <div className="flex gap-6">
        {/* Sidebar */}
        <div className="w-60 shrink-0 space-y-2">
          <Button onClick={() => { setSelected(null); setNodes(DEFAULT_NODES); setEdges(DEFAULT_EDGES); }}
            className="w-full justify-start bg-slate-700 text-sm text-slate-200">
            <Plus className="mr-2 h-4 w-4" /> New Workflow
          </Button>
          {isLoading ? (
            Array.from({ length: 3 }).map((_, i) => <SkeletonRow key={i} />)
          ) : workflows.length === 0 ? (
            <EmptyState title="No workflows" description="Create your first." />
          ) : (
            workflows.map((w) => (
              <button key={w.id} onClick={() => loadWorkflow(w)}
                className={`w-full rounded-xl border p-3 text-left text-sm transition-colors ${
                  selected?.id === w.id
                    ? "border-cyan-500/40 bg-cyan-500/10 text-cyan-200"
                    : "border-border/40 text-slate-300 hover:bg-slate-800/40"
                }`}>
                <p className="font-medium">{w.name}</p>
                <div className="mt-0.5 flex items-center gap-2">{statusBadge(w.status ?? "active")}</div>
              </button>
            ))
          )}
        </div>

        {/* Editor */}
        <div className="flex-1 space-y-3">
          <div className="flex gap-2">
            <Button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
              <Save className="mr-2 h-4 w-4" />
              {saveMutation.isPending ? "Saving…" : "Save"}
            </Button>
            {selected && (
              <Button onClick={() => executeMutation.mutate(selected.id)} disabled={executeMutation.isPending}
                className="bg-emerald-500/20 text-emerald-300 hover:bg-emerald-500/30">
                <Play className="mr-2 h-4 w-4" />
                {executeMutation.isPending ? "Starting…" : "Test Run"}
              </Button>
            )}
          </div>
          <div className="h-[540px] rounded-2xl border border-border/60 bg-card/70">
            <ReactFlow nodes={nodes} edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              fitView>
              <Background />
              <MiniMap />
              <Controls />
            </ReactFlow>
          </div>
        </div>
      </div>
    </PageShell>
  );
}

