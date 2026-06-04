import { useMemo, useState } from "react";
import { Card } from "../components/Card";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import { currency } from "../utils/helpers";

export default function Reports() {
  const invoices = useCRMStore((state) => state.invoices);
  const clients = useCRMStore((state) => state.clients);
  const [filter, setFilter] = useState("all");

  const rows = useMemo(
    () => invoices.filter((invoice) => (filter === "all" ? true : invoice.status === filter)),
    [invoices, filter]
  );

  const total = rows.reduce((sum, row) => sum + row.amount, 0);
  const exportReady = rows.filter((row) => row.status === "paid").length;

  return (
    <div className="stack">
      <div className="grid cols-3">
        <Card title="Report Decks">
          <h2>{rows.length}</h2>
          <p className="muted">Generated business snapshots</p>
        </Card>
        <Card title="Export Status">
          <h2>{exportReady} ready</h2>
          <p className="muted">PDF + CSV bundles synchronized</p>
        </Card>
        <Card title="Analytics Summary">
          <h2>{currency(total)}</h2>
          <p className="muted">Current filtered invoice value</p>
        </Card>
      </div>

      <Card title="Report Studio" subtitle="Visual report thumbnails and exports">
        <div className="row">
          <Select value={filter} onChange={(event) => setFilter(event.target.value)}>
            <option value="all">All invoices</option>
            <option value="draft">Draft</option>
            <option value="sent">Sent</option>
            <option value="paid">Paid</option>
            <option value="overdue">Overdue</option>
          </Select>
          <span className="badge badge-info">PDF rendering cluster active</span>
        </div>

        <div className="report-grid">
          {rows.map((row) => (
            <article key={row.id} className="report-card">
              <div className="report-thumb">PDF Preview</div>
              <h3>{row.id}</h3>
              <p>{clients.find((client) => client.id === row.clientId)?.name ?? "Unknown"}</p>
              <span>{currency(row.amount)}</span>
            </article>
          ))}
        </div>
      </Card>

      <Card title="Report Records">
        <Table
          rows={rows}
          emptyMessage="No report rows for this filter."
          columns={[
            { header: "Invoice", render: (row) => row.id },
            { header: "Client", render: (row) => clients.find((client) => client.id === row.clientId)?.name ?? "Unknown" },
            { header: "Status", render: (row) => row.status },
            { header: "Issued", render: (row) => row.issuedDate },
            { header: "Amount", render: (row) => currency(row.amount) },
          ]}
        />
      </Card>
    </div>
  );
}
