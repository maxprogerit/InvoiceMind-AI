import { useMemo, useState } from "react";
import { Card } from "../components/Card";
import { FileUpload } from "../components/FileUpload";
import { Input } from "../components/Input";
import { Select } from "../components/Select";
import { useCRMStore } from "../store/crmStore";
import { byQuery } from "../utils/helpers";

export default function Documents() {
  const documents = useCRMStore((state) => state.documents);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("all");

  const rows = useMemo(
    () =>
      byQuery(documents, query, (doc) => `${doc.name} ${doc.linkedTo}`)
        .filter((doc) => (filter === "all" ? true : doc.type === filter)),
    [documents, query, filter]
  );

  return (
    <div className="stack">
      <Card title="Upload Intelligence Package" subtitle="Add invoices, receipts, and contracts to the AI mesh">
        <FileUpload />
      </Card>

      <Card title="Document Stream" subtitle="Premium AI extraction views with confidence telemetry">
        <div className="grid cols-2">
          <Input placeholder="Search document..." value={query} onChange={(event) => setQuery(event.target.value)} />
          <Select value={filter} onChange={(event) => setFilter(event.target.value)}>
            <option value="all">All types</option>
            <option value="general">General</option>
            <option value="contract">Contract</option>
            <option value="invoice">Invoice</option>
            <option value="receipt">Receipt</option>
            <option value="legal">Legal</option>
          </Select>
        </div>

        {!rows.length && <div className="empty-state">No documents in the current filter.</div>}

        <div className="doc-grid">
          {rows.map((doc, index) => {
            const confidence = Math.max(74, 98 - (index % 7) * 3);
            return (
              <article key={doc.id} className="doc-card">
                <div className="doc-preview">
                  <span>{doc.type.toUpperCase()}</span>
                  <small>AI Scan Overlay</small>
                </div>
                <div className="doc-meta">
                  <h3>{doc.name}</h3>
                  <p>{doc.linkedTo}</p>
                  <div className="doc-stats">
                    <span>Status: Extracting fields</span>
                    <span>Confidence: {confidence}%</span>
                    <span>Size: {Math.round(doc.size / 1024)} KB</span>
                    <span>Workflow: Validation</span>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      </Card>
    </div>
  );
}
