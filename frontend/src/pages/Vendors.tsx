import { Card } from "../components/Card";
import { useCRMStore } from "../store/crmStore";
import { currency } from "../utils/helpers";

export default function Vendors() {
  const companies = useCRMStore((state) => state.companies);
  const clients = useCRMStore((state) => state.clients);
  const invoices = useCRMStore((state) => state.invoices);

  return (
    <div className="stack">
      <Card title="Vendor Intelligence Profiles" subtitle="Spending history, trust score, and payment analytics">
        <div className="vendor-grid">
          {companies.map((company, index) => {
            const relatedClient = clients[index % clients.length];
            const value = invoices
              .filter((invoice) => invoice.clientId === relatedClient?.id)
              .reduce((sum, invoice) => sum + invoice.amount, 0);

            return (
              <article key={company.id} className="vendor-card">
                <h3>{company.name}</h3>
                <p>{company.industry}</p>
                <div className="vendor-stats">
                  <span>Trust score: {95 - index * 3}%</span>
                  <span>Invoice volume: {Math.max(2, index + 2)}</span>
                  <span>Spending: {currency(value || 9200 + index * 1800)}</span>
                  <span>Payment health: Stable</span>
                </div>
              </article>
            );
          })}
        </div>
      </Card>
    </div>
  );
}
