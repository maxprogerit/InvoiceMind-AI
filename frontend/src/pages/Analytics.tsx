import { Card } from "../components/Card";
import { useCRMStore } from "../store/crmStore";
import { Bar, BarChart, CartesianGrid, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export default function Analytics() {
  const deals = useCRMStore((state) => state.deals);
  const invoices = useCRMStore((state) => state.invoices);

  const stageData = ["lead", "qualified", "proposal", "won", "lost"].map((stage) => ({
    label: stage,
    value: deals.filter((deal) => deal.stage === stage).length,
  }));

  const invoiceData = ["draft", "sent", "paid", "overdue"].map((status) => ({
    name: status,
    value: invoices.filter((invoice) => invoice.status === status).length,
  }));

  return (
    <div className="grid cols-2">
      <Card title="Deals by Stage" subtitle="Bar chart">
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
      <Card title="Invoice Status Split" subtitle="Pie chart">
        <div style={{ width: "100%", height: 300 }}>
          <ResponsiveContainer>
            <PieChart>
              <Pie data={invoiceData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={90} fill="#2563eb" label />
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </Card>
    </div>
  );
}
