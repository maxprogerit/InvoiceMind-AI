import { Bar, BarChart, CartesianGrid, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Card } from "../components/Card";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import { currency } from "../utils/helpers";

export default function Analytics() {
  const clients = useCRMStore((state) => state.clients);
  const deals = useCRMStore((state) => state.deals);
  const invoices = useCRMStore((state) => state.invoices);
  const receipts = useCRMStore((state) => state.receipts);

  const totalRevenue = invoices.filter((invoice) => invoice.status === "Paid").reduce((sum, invoice) => sum + invoice.total, 0);
  const totalExpenses = receipts.reduce((sum, receipt) => sum + receipt.amount, 0);
  const wonDeals = deals.filter((deal) => deal.stage === "Won").length;
  const lostDeals = deals.filter((deal) => deal.stage === "Lost").length;
  const activeDeals = deals.filter((deal) => deal.stage !== "Won" && deal.stage !== "Lost").length;
  const winRate = wonDeals + lostDeals ? (wonDeals / (wonDeals + lostDeals)) * 100 : 0;
  const forecastRevenue = deals.reduce((sum, deal) => sum + (deal.value * deal.probability) / 100, 0);

  const stageData = ["New", "Qualified", "Proposal", "Negotiation", "Won", "Lost"].map((stage) => ({
    label: stage,
    value: deals.filter((deal) => deal.stage === stage).reduce((sum, deal) => sum + deal.value, 0),
  }));

  const invoiceStatusData = ["Draft", "Pending", "Paid", "Overdue"].map((status) => ({
    name: status,
    value: invoices.filter((invoice) => invoice.status === status).length,
  }));

  const receiptCategoryData = ["Travel", "Software", "Office", "Marketing", "Operations", "Other"].map((category) => ({
    label: category,
    value: receipts.filter((receipt) => receipt.category === category).reduce((sum, receipt) => sum + receipt.amount, 0),
  }));

  const topClients = [...clients].sort((a, b) => b.estimatedValue - a.estimatedValue).slice(0, 5);

  const clientGrowth = Object.values(
    clients.reduce<Record<string, { month: string; count: number }>>((acc, client) => {
      const month = client.lastContactDate.slice(0, 7) || "Unknown";
      acc[month] = acc[month] || { month, count: 0 };
      acc[month].count += 1;
      return acc;
    }, {})
  );

  return (
    <div className="stack">
      <div className="grid cols-4">
        <Card title="Total Revenue">
          <h2>{currency(totalRevenue)}</h2>
        </Card>
        <Card title="Total Expenses">
          <h2>{currency(totalExpenses)}</h2>
        </Card>
        <Card title="Profit">
          <h2>{currency(totalRevenue - totalExpenses)}</h2>
        </Card>
        <Card title="Forecast Revenue">
          <h2>{currency(forecastRevenue)}</h2>
        </Card>
      </div>

      <div className="grid cols-4">
        <Card title="Active Deals">
          <h2>{activeDeals}</h2>
        </Card>
        <Card title="Won Deals">
          <h2>{wonDeals}</h2>
        </Card>
        <Card title="Lost Deals">
          <h2>{lostDeals}</h2>
        </Card>
        <Card title="Win Rate">
          <h2>{winRate.toFixed(1)}%</h2>
        </Card>
      </div>

      <div className="grid cols-2">
        <Card title="Sales Pipeline Chart Data">
          <div style={{ width: "100%", height: 300 }}>
            <ResponsiveContainer>
              <BarChart data={stageData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="label" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="value" fill="#7c3aed" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title="Invoice Status Distribution">
          <div style={{ width: "100%", height: 300 }}>
            <ResponsiveContainer>
              <PieChart>
                <Pie data={invoiceStatusData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={90} fill="#2563eb" label />
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      <div className="grid cols-2">
        <Card title="Receipt Expense Categories">
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={receiptCategoryData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="label" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="value" fill="#ef4444" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title="Client Growth">
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={clientGrowth}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="count" fill="#22c55e" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      <Card title="Top Clients">
        <Table
          rows={topClients}
          emptyMessage="No client data yet."
          columns={[
            { header: "Client", render: (row) => row.name },
            { header: "Industry", render: (row) => row.industry },
            { header: "Estimated Value", render: (row) => currency(row.estimatedValue) },
          ]}
        />
      </Card>
    </div>
  );
}
