"use client";

import ReactFlow, { Controls, Background, MiniMap, Node, Edge } from "reactflow";
import "reactflow/dist/style.css";

const nodes: Node[] = [
  { id: "1", position: { x: 0, y: 50 }, data: { label: "Upload Document" }, type: "input" },
  { id: "2", position: { x: 250, y: 50 }, data: { label: "AI OCR Node" } },
  { id: "3", position: { x: 500, y: 50 }, data: { label: "Validation Node" } },
  { id: "4", position: { x: 750, y: 50 }, data: { label: "Approval Node" } },
  { id: "5", position: { x: 1000, y: 50 }, data: { label: "ERP Export Node" }, type: "output" }
];

const edges: Edge[] = [
  { id: "e1-2", source: "1", target: "2", animated: true },
  { id: "e2-3", source: "2", target: "3", animated: true },
  { id: "e3-4", source: "3", target: "4", animated: true },
  { id: "e4-5", source: "4", target: "5", animated: true }
];

export function WorkflowEditor() {
  return (
    <div className="h-[580px] rounded-2xl border border-border/60 bg-card/70">
      <ReactFlow nodes={nodes} edges={edges} fitView>
        <Background />
        <MiniMap />
        <Controls />
      </ReactFlow>
    </div>
  );
}
