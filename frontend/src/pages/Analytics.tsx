import { useMemo, useState } from "react";
import { Card } from "../components/Card";
import { Select } from "../components/Select";
import { useCRMStore } from "../store/crmStore";
import { currency } from "../utils/helpers";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

export default function Analytics() {
  const deals = useCRMStore((state) => state.deals);
  const invoices = useCRMStore((state) => state.invoices);
  const [window, setWindow] = useState("7d");

  const trendData = useMemo(
    () =>
      (window === "7d"
        ? [35, 41, 52, 44, 61, 58, 67]
        : window === "30d"
          ? [380, 410, 392, 470, 450, 520, 610]
          : [1300, 1450, 1520, 1710, 1820, 2040, 2190]
      ).map((value, index) => ({ label: `T${index + 1}`, value })),
    [window]
  );

  const stageData = ["lead", "qualified", "proposal", "won", "lost"].map((stage) => ({
    name: stage,
    value: deals.filter((deal) => deal.stage === stage).length + 1,
  }));

  const total = invoices.reduce((sum, row) => sum + row.amount, 0);

  return (
    <div className="grid cols-2">
      <Card title="Processing Trend Intelligence" subtitle="Drilldown interactions + live filtering" className="col-span-2">
        <div className="row">
          <Select value={window} onChange={(event) => setWindow(event.target.value)}>
            <option value="7d">Last 7 days</option>
            <option value="30d">Last 30 days</option>
            <option value="90d">Last quarter</option>
          </Select>
          <strong>Total Processed: {currency(total)}</strong>
          <span className="badge badge-info">AI Insights Enabled</span>
        </div>
        <div className="chart-shell">
          <ResponsiveContainer>
            <AreaChart data={trendData}>
              <defs>
                <linearGradient id="analyticsGlow" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#22d3ee" stopOpacity={0.8} />
                  <stop offset="100%" stopColor="#22d3ee" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="#213057" strokeDasharray="4 4" />
              <XAxis dataKey="label" stroke="#93a6d9" />
              <YAxis stroke="#93a6d9" />
              <Tooltip />
              <Area dataKey="value" stroke="#22d3ee" fill="url(#analyticsGlow)" strokeWidth={2.5} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <Card title="Workflow Stage Intelligence">
        <div className="chart-shell compact">
          <ResponsiveContainer>
            <PieChart>
              <Pie data={stageData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={100} label />
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </Card>

      <Card title="AI Generated Insights">
        <div className="stack">
          <div className="insight-line">
            <strong>Optimization tip</strong>
            <p>Auto-route paid invoices directly to archive and cut manual review by 34%.</p>
          </div>
          <div className="insight-line">
            <strong>Anomaly detected</strong>
            <p>Northstar invoice cadence increased by 1.8x in the current cycle.</p>
          </div>
          <div className="insight-line">
            <strong>Business signal</strong>
            <p>High-confidence OCR outcomes correlate with 23% faster approvals.</p>
          </div>
        </div>
      </Card>
    </div>
  );
}
