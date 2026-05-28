import { useMemo, useState } from "react";
import { Badge } from "../components/Badge";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { Company } from "../types/types";
import { byQuery, currency } from "../utils/helpers";

const defaultForm: Omit<Company, "id"> = {
  name: "",
  industry: "",
  website: "",
  size: "",
  revenue: 0,
  status: "active",
  notes: "",
};

export default function Companies() {
  const companies = useCRMStore((state) => state.companies);
  const contacts = useCRMStore((state) => state.contacts);
  const deals = useCRMStore((state) => state.deals);
  const addCompany = useCRMStore((state) => state.addCompany);
  const updateCompany = useCRMStore((state) => state.updateCompany);
  const deleteCompany = useCRMStore((state) => state.deleteCompany);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("all");
  const [open, setOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [selectedCompanyId, setSelectedCompanyId] = useState<string | null>(null);
  const [form, setForm] = useState(defaultForm);

  const rows = useMemo(
    () =>
      byQuery(companies, query, (item) => `${item.name} ${item.industry}`)
        .filter((item) => (status === "all" ? true : item.status === status)),
    [companies, query, status]
  );

  const selectedCompany = companies.find((company) => company.id === selectedCompanyId) ?? null;

  const healthScore = (company: Company) => {
    const companyDeals = deals.filter((deal) => deal.company === company.name);
    const total = companyDeals.reduce((sum, deal) => sum + deal.value * (deal.probability / 100), 0);
    const normalized = Math.min(100, Math.round((total / Math.max(company.revenue || 1, 1)) * 200));
    return normalized;
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (form.revenue < 0) {
      pushToast("Revenue must be positive.", "error");
      return;
    }
    if (editId) {
      updateCompany(editId, form);
      pushToast("Company updated successfully.", "success");
    } else {
      addCompany(form);
      pushToast("Company created successfully.", "success");
    }
    setOpen(false);
    setEditId(null);
    setForm(defaultForm);
  };

  return (
    <Card title="Companies" subtitle="Create, search, filter and manage company profiles" actions={<Button onClick={() => setOpen(true)}>Add Company</Button>}>
      <div className="grid cols-4">
        <Input placeholder="Search company..." value={query} onChange={(event) => setQuery(event.target.value)} />
        <Select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="all">All statuses</option>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
        </Select>
      </div>

      <Table
        rows={rows}
        emptyMessage="No companies yet."
        columns={[
          { header: "Name", render: (row) => row.name },
          { header: "Industry", render: (row) => row.industry },
          { header: "Size", render: (row) => row.size },
          { header: "Revenue", render: (row) => currency(row.revenue) },
          { header: "Health Score", render: (row) => `${healthScore(row)} / 100` },
          {
            header: "Status",
            render: (row) => <Badge tone={row.status === "active" ? "success" : "warning"}>{row.status}</Badge>,
          },
          {
            header: "Actions",
            render: (row) => (
              <div className="row">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setEditId(row.id);
                    setForm({ ...row });
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => {
                    setSelectedCompanyId(row.id);
                    setDetailOpen(true);
                  }}
                >
                  View Details
                </Button>
                <Button
                  variant="danger"
                  onClick={() => {
                    if (!window.confirm("Delete this company?")) return;
                    deleteCompany(row.id);
                    pushToast("Company deleted.", "info");
                  }}
                >
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />

      <Modal title={editId ? "Edit Company" : "Create Company"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Input placeholder="Name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required />
          <Input placeholder="Industry" value={form.industry} onChange={(event) => setForm({ ...form, industry: event.target.value })} required />
          <Input placeholder="Website" value={form.website} onChange={(event) => setForm({ ...form, website: event.target.value })} required />
          <Input placeholder="Size (e.g. 51-200)" value={form.size} onChange={(event) => setForm({ ...form, size: event.target.value })} required />
          <Input type="number" min={0} placeholder="Revenue" value={form.revenue} onChange={(event) => setForm({ ...form, revenue: Number(event.target.value) })} required />
          <Select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as Company["status"] })}>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </Select>
          <Input placeholder="Notes" value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} required />
          <Button type="submit">{editId ? "Save Company" : "Create Company"}</Button>
        </form>
      </Modal>

      <Modal title="Company Profile" open={detailOpen} onClose={() => setDetailOpen(false)}>
        {selectedCompany ? (
          <div className="stack">
            <p>
              <strong>{selectedCompany.name}</strong> - {selectedCompany.industry}
            </p>
            <p className="muted">{selectedCompany.notes}</p>
            <Card title="Linked Contacts">
              <Table
                rows={contacts.filter((contact) => contact.companyId === selectedCompany.id)}
                emptyMessage="No linked contacts."
                columns={[
                  { header: "Name", render: (row) => row.name },
                  { header: "Email", render: (row) => row.email },
                  { header: "Role", render: (row) => row.role },
                ]}
              />
            </Card>
            <Card title="Linked Deals">
              <Table
                rows={deals.filter((deal) => deal.company === selectedCompany.name)}
                emptyMessage="No linked deals."
                columns={[
                  { header: "Title", render: (row) => row.title },
                  { header: "Stage", render: (row) => row.stage },
                  { header: "Value", render: (row) => currency(row.value) },
                ]}
              />
            </Card>
          </div>
        ) : (
          <p className="muted">No company selected.</p>
        )}
      </Modal>
    </Card>
  );
}
