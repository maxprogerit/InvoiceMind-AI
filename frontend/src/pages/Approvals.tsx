import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { useCRMStore } from "../store/crmStore";
import { currency } from "../utils/helpers";

export default function Approvals() {
  const invoices = useCRMStore((state) => state.invoices);
  const documents = useCRMStore((state) => state.documents);
  const pushToast = useCRMStore((state) => state.pushToast);

  return (
    <div className="stack">
      <Card title="Premium Review Center" subtitle="AI recommendations + extraction confidence">
        <div className="approval-grid">
          {invoices.map((invoice, index) => (
            <article key={invoice.id} className="approval-card">
              <div className="approval-preview">Document Preview</div>
              <div>
                <h3>{invoice.id}</h3>
                <p className="muted">Linked file: {documents[index % Math.max(1, documents.length)]?.name ?? "N/A"}</p>
                <div className="approval-stats">
                  <span>Extracted amount: {currency(invoice.amount)}</span>
                  <span>Confidence score: {97 - index * 2}%</span>
                  <span>AI recommendation: Approve</span>
                </div>
              </div>
              <div className="row">
                <Button onClick={() => pushToast(`${invoice.id} approved`, "success")}>Approve</Button>
                <Button variant="secondary" onClick={() => pushToast(`${invoice.id} sent for review`, "info")}>
                  Review
                </Button>
              </div>
            </article>
          ))}
        </div>
      </Card>
    </div>
  );
}
