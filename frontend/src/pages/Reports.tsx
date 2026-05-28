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

  return (
    <div className="stack">
      <Card title="Reports" subtitle="Finance tables, filters, and summary">
        <div className="row">
          <Select value={filter} onChange={(event) => setFilter(event.target.value)}>
            <option value="all">All invoices</option>
            <option value="draft">Draft</option>
            <option value="sent">Sent</option>
            <option value="paid">Paid</option>
            <option value="overdue">Overdue</option>
          </Select>
          <strong>Total: {currency(total)}</strong>
        </div>
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
