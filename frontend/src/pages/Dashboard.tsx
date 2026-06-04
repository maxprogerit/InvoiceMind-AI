import { Card } from "../components/Card";
import { CountUp } from "../components/CountUp";
import { useCRMStore } from "../store/crmStore";
import { currency } from "../utils/helpers";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

export default function Dashboard() {
  const documents = useCRMStore((state) => state.documents);
  const companies = useCRMStore((state) => state.companies);
  const invoices = useCRMStore((state) => state.invoices);

  const totalInvoiceValue = invoices.reduce((sum, item) => sum + item.amount, 0);
  const automationRate = 93.4;
  const ocrAccuracy = 98.1;
  const speed = 1.7;
  const queue = invoices.filter((invoice) => invoice.status !== "paid").length;

  const volumeData = [
    { label: "Mon", value: 22 },
    { label: "Tue", value: 31 },
    { label: "Wed", value: 26 },
    { label: "Thu", value: 42 },
    { label: "Fri", value: 36 },
    { label: "Sat", value: 28 },
    { label: "Sun", value: 45 },
  ];

  const confidenceData = [
    { label: "OCR", value: 98 },
    { label: "Validation", value: 94 },
    { label: "Approval", value: 89 },
    { label: "Export", value: 97 },
  ];

  return (
    <div className="stack">
      <Card className="hero-card">
        <div className="hero-grid">
          <div>
            <p className="kicker">AI Command</p>
            <h2 className="hero-title">InvoiceMind AI</h2>
            <p className="muted">AI Document Automation Platform</p>
          </div>
          <div className="status-orb">
            <span className="orb-pulse" />
            <p>Neural Core Active</p>
          </div>
        </div>
      </Card>

      <div className="grid cols-5">
        <Card title="Total Documents">
          <h2>
            <CountUp value={documents.length} />
          </h2>
        </Card>
        <Card title="Automation Rate">
          <h2>
            <CountUp value={automationRate} decimals={1} suffix="%" />
          </h2>
        </Card>
        <Card title="OCR Accuracy">
          <h2>
            <CountUp value={ocrAccuracy} decimals={1} suffix="%" />
          </h2>
        </Card>
        <Card title="Processing Speed">
          <h2>
            <CountUp value={speed} decimals={1} suffix="s" />
          </h2>
        </Card>
        <Card title="Approval Queue">
          <h2>
            <CountUp value={queue} />
          </h2>
        </Card>
      </div>

      <div className="grid cols-3">
        <Card title="Document Volume & Processing Trends" subtitle="Live orchestration analytics" className="col-span-2">
          <div className="chart-shell">
            <ResponsiveContainer>
              <AreaChart data={volumeData}>
                <defs>
                  <linearGradient id="volumeGlow" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#3b82f6" stopOpacity={0.75} />
                    <stop offset="100%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#213057" strokeDasharray="3 3" />
                <XAxis dataKey="label" stroke="#93a6d9" />
                <YAxis stroke="#93a6d9" />
                <Tooltip />
                <Area dataKey="value" stroke="#60a5fa" fill="url(#volumeGlow)" strokeWidth={2.5} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card title="AI Processing Center" subtitle="Realtime neural activity">
          <div className="stack">
            <div className="processing-line">
              <span className="dot active" /> OCR job running · Apex-MSA.pdf
            </div>
            <div className="processing-line">
              <span className="dot active" /> Workflow execution · Vendor match
            </div>
            <div className="processing-line">
              <span className="dot" /> Approval recommendation · Queue optimization
            </div>
            <div className="processing-line">
              <span className="dot" /> Export push · ERP bridge
            </div>
            <div className="activity-feed">
              <p>AI Recommendations</p>
              <ul>
                <li>Auto-approve 2 invoices above 96% confidence</li>
                <li>Flag Northstar spend deviation (+18%)</li>
                <li>Archive 4 processed receipts</li>
              </ul>
            </div>
          </div>
        </Card>
      </div>

      <div className="grid cols-2">
        <Card title="Vendor Spending Topline">
          <h2>{currency(totalInvoiceValue)}</h2>
          <p className="muted">{companies.length} active vendors in analytics mesh</p>
        </Card>
        <Card title="AI Confidence Score" subtitle="Pipeline stage confidence blend">
          <div className="chart-shell compact">
            <ResponsiveContainer>
              <BarChart data={confidenceData}>
                <CartesianGrid stroke="#213057" strokeDasharray="3 3" />
                <XAxis dataKey="label" stroke="#93a6d9" />
                <YAxis stroke="#93a6d9" />
                <Tooltip />
                <Bar dataKey="value" fill="#7c3aed" radius={[8, 8, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>
    </div>
  );
}
