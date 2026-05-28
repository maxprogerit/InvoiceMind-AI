import { Card } from "../components/Card";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import { currency } from "../utils/helpers";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export default function Dashboard() {
  const clients = useCRMStore((state) => state.clients);
  const deals = useCRMStore((state) => state.deals);
  const invoices = useCRMStore((state) => state.invoices);

  const chartData = deals.map((deal) => ({
    label: deal.title.slice(0, 12),
    value: deal.value,
  }));

  return (
    <div className="stack">
      <div className="grid cols-4">
        <Card title="Clients">
          <h2>{clients.length}</h2>
        </Card>
        <Card title="Deals">
          <h2>{deals.length}</h2>
        </Card>
        <Card title="Open Invoices">
          <h2>{invoices.filter((invoice) => invoice.status !== "paid").length}</h2>
        </Card>
        <Card title="Invoice Value">
          <h2>{currency(invoices.reduce((sum, item) => sum + item.amount, 0))}</h2>
        </Card>
      </div>

      <Card title="Deal Pipeline Value" subtitle="Chart">
        <div style={{ width: "100%", height: 280 }}>
          <ResponsiveContainer>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="label" />
              <YAxis />
              <Tooltip />
              <Bar dataKey="value" fill="#2563eb" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <Card title="Recent Invoices" subtitle="Table">
        <Table
          rows={invoices.slice(0, 6)}
          emptyMessage="No invoices yet."
          columns={[
            { header: "Invoice", render: (row) => row.id },
            {
              header: "Client",
              render: (row) => clients.find((client) => client.id === row.clientId)?.name ?? "Unknown",
            },
            { header: "Amount", render: (row) => currency(row.amount) },
            { header: "Status", render: (row) => row.status },
            { header: "Due Date", render: (row) => row.dueDate },
          ]}
        />
      </Card>
    </div>
  );
}
