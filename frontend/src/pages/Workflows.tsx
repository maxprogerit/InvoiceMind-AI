import { Card } from "../components/Card";

const nodes = [
  { id: "upload", label: "Upload" },
  { id: "ocr", label: "OCR" },
  { id: "validation", label: "Validation" },
  { id: "approval", label: "Approval" },
  { id: "export", label: "Export" },
  { id: "archive", label: "Archive" },
];

export default function Workflows() {
  return (
    <div className="stack">
      <Card title="AI Automation Network" subtitle="Live execution visualization with pulsing edges">
        <div className="workflow-canvas">
          <div className="workflow-edges">
            {[...Array(5)].map((_, index) => (
              <span key={index} className="workflow-edge" />
            ))}
          </div>
          <div className="workflow-nodes">
            {nodes.map((node) => (
              <div key={node.id} className={`workflow-node node-${node.id}`}>
                <strong>{node.label}</strong>
                <small>Active</small>
              </div>
            ))}
          </div>
        </div>
      </Card>
    </div>
  );
}
