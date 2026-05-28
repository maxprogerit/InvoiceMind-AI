import { useMemo, useState } from "react";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import { currency } from "../utils/helpers";
import type { TableColumn } from "../components/Table";

type ReportType = "sales" | "revenue" | "invoice" | "receipt" | "client-growth" | "team-performance";
type ReportResult = {
  title: string;
  rows: Record<string, unknown>[];
  columns: TableColumn<Record<string, unknown>>[];
};

export default function Reports() {
  const invoices = useCRMStore((state) => state.invoices);
  const receipts = useCRMStore((state) => state.receipts);
  const clients = useCRMStore((state) => state.clients);
  const deals = useCRMStore((state) => state.deals);
  const team = useCRMStore((state) => state.team);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [reportType, setReportType] = useState<ReportType>("sales");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  const inRange = (date: string) => (!fromDate || date >= fromDate) && (!toDate || date <= toDate);

  const report: ReportResult = useMemo(() => {
    if (reportType === "sales") {
      const rows = deals.filter((deal) => inRange(deal.expectedCloseDate));
      return {
        title: "Sales Report",
        rows: rows as unknown as Record<string, unknown>[],
        columns: [
          { header: "Deal", render: (row) => String(row.title ?? "") },
          { header: "Stage", render: (row) => String(row.stage ?? "") },
          { header: "Value", render: (row) => currency(Number(row.value ?? 0)) },
          { header: "Forecast", render: (row) => currency((Number(row.value ?? 0) * Number(row.probability ?? 0)) / 100) },
        ],
      };
    }
    if (reportType === "revenue") {
      const rows = invoices.filter((invoice) => inRange(invoice.issuedDate));
      return {
        title: "Revenue Report",
        rows: rows as unknown as Record<string, unknown>[],
        columns: [
          { header: "Invoice", render: (row) => String(row.invoiceNumber ?? "") },
          { header: "Status", render: (row) => String(row.status ?? "") },
          { header: "Total", render: (row) => currency(Number(row.total ?? 0)) },
          { header: "Issued", render: (row) => String(row.issuedDate ?? "") },
        ],
      };
    }
    if (reportType === "invoice") {
      const rows = invoices.filter((invoice) => inRange(invoice.dueDate));
      return {
        title: "Invoice Report",
        rows: rows as unknown as Record<string, unknown>[],
        columns: [
          { header: "Invoice", render: (row) => String(row.invoiceNumber ?? "") },
          { header: "Client", render: (row) => clients.find((client) => client.id === row.clientId)?.name ?? "Unknown" },
          { header: "Due Date", render: (row) => String(row.dueDate ?? "") },
          { header: "Status", render: (row) => String(row.status ?? "") },
        ],
      };
    }
    if (reportType === "receipt") {
      const rows = receipts.filter((receipt) => inRange(receipt.date));
      return {
        title: "Receipt Expense Report",
        rows: rows as unknown as Record<string, unknown>[],
        columns: [
          { header: "Receipt", render: (row) => String(row.receiptNumber ?? "") },
          { header: "Vendor", render: (row) => String(row.vendor ?? "") },
          { header: "Category", render: (row) => String(row.category ?? "") },
          { header: "Amount", render: (row) => currency(Number(row.amount ?? 0)) },
        ],
      };
    }
    if (reportType === "client-growth") {
      const rows = clients.filter((client) => inRange(client.lastContactDate));
      return {
        title: "Client Growth Report",
        rows: rows as unknown as Record<string, unknown>[],
        columns: [
          { header: "Client", render: (row) => String(row.name ?? "") },
          { header: "Industry", render: (row) => String(row.industry ?? "") },
          { header: "Value", render: (row) => currency(Number(row.estimatedValue ?? 0)) },
          { header: "Last Contact", render: (row) => String(row.lastContactDate ?? "") },
        ],
      };
    }
    const rows = team;
    return {
      title: "Team Performance Report",
      rows: rows as unknown as Record<string, unknown>[],
      columns: [
        { header: "Member", render: (row) => String(row.name ?? "") },
        { header: "Role", render: (row) => String(row.role ?? "") },
        { header: "Tasks Completed", render: (row) => Number(row.tasksCompleted ?? 0) },
        { header: "Revenue Generated", render: (row) => currency(Number(row.revenueGenerated ?? 0)) },
      ],
    };
  }, [reportType, deals, invoices, receipts, clients, team, fromDate, toDate]);

  const total = useMemo(() => {
    if (reportType === "sales") return deals.filter((deal) => inRange(deal.expectedCloseDate)).reduce((sum, deal) => sum + deal.value, 0);
    if (reportType === "revenue" || reportType === "invoice") return invoices.filter((invoice) => inRange(invoice.issuedDate)).reduce((sum, invoice) => sum + invoice.total, 0);
    if (reportType === "receipt") return receipts.filter((receipt) => inRange(receipt.date)).reduce((sum, receipt) => sum + receipt.amount, 0);
    if (reportType === "client-growth") return clients.filter((client) => inRange(client.lastContactDate)).length;
    return team.reduce((sum, member) => sum + member.revenueGenerated, 0);
  }, [reportType, deals, invoices, receipts, clients, team, fromDate, toDate]);

  return (
    <div className="stack">
      <Card title="Report Builder" subtitle="Generate dynamic reports from current CRM state">
        <div className="grid cols-4">
          <Select value={reportType} onChange={(event) => setReportType(event.target.value as ReportType)}>
            <option value="sales">Sales Report</option>
            <option value="revenue">Revenue Report</option>
            <option value="invoice">Invoice Report</option>
            <option value="receipt">Receipt Expense Report</option>
            <option value="client-growth">Client Growth Report</option>
            <option value="team-performance">Team Performance Report</option>
          </Select>
          <Input type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} />
          <Input type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} />
        </div>
        <div className="row">
          <strong>{report.title}</strong>
          <span className="muted">
            {reportType === "client-growth" ? `Total Clients: ${total}` : `Total: ${currency(Number(total) || 0)}`}
          </span>
          <Button variant="secondary" onClick={() => pushToast("PDF export generated.", "success")}>
            Export PDF
          </Button>
          <Button variant="secondary" onClick={() => pushToast("CSV export generated.", "success")}>
            Export CSV
          </Button>
          <Button variant="secondary" onClick={() => pushToast("Excel export generated.", "success")}>
            Export Excel
          </Button>
        </div>
      </Card>
      <Card title="Report Preview">
        <Table rows={report.rows} emptyMessage="No report rows for selected criteria." columns={report.columns} />
      </Card>
    </div>
  );
}
