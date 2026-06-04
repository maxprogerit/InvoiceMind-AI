import { Card } from "../components/Card";

const stages = ["Upload", "OCR", "Validation", "Approval", "Export", "Archive"];

export default function Executions() {
  return (
    <div className="stack">
      <Card title="Execution Graph" subtitle="NASA mission control meets AI infrastructure">
        <div className="execution-network">
          {stages.map((stage, index) => (
            <div key={stage} className={`execution-node ${index < 4 ? "active" : ""}`}>
              <strong>{stage}</strong>
              <span>{index < 4 ? "Running" : "Queued"}</span>
            </div>
          ))}
        </div>
      </Card>

      <div className="grid cols-2">
        <Card title="Workflow Stages">
          <div className="stack">
            {stages.map((stage, index) => (
              <div key={stage} className="processing-line">
                <span className={`dot ${index < 4 ? "active" : ""}`} />
                {stage} stage · {index < 4 ? "processing packets" : "awaiting trigger"}
              </div>
            ))}
          </div>
        </Card>
        <Card title="Realtime Logs">
          <div className="log-stream">
            <p>[22:04:11] OCR confidence 97.8% on Apex-MSA.pdf</p>
            <p>[22:04:14] Vendor entity matched to Apex Labs</p>
            <p>[22:04:15] Validation rule #14 passed</p>
            <p>[22:04:18] Approval recommendation queued</p>
            <p>[22:04:20] Export payload generated</p>
          </div>
        </Card>
      </div>
    </div>
  );
}
