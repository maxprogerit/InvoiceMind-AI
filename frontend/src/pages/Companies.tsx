import { useMemo, useState } from "react";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { Company } from "../types/types";
import { byQuery } from "../utils/helpers";

const defaultForm: Omit<Company, "id"> = {
  name: "",
  industry: "",
  size: "",
  website: "",
};

export default function Companies() {
  const companies = useCRMStore((state) => state.companies);
  const addCompany = useCRMStore((state) => state.addCompany);
  const updateCompany = useCRMStore((state) => state.updateCompany);
  const deleteCompany = useCRMStore((state) => state.deleteCompany);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [form, setForm] = useState(defaultForm);

  const rows = useMemo(() => byQuery(companies, query, (item) => `${item.name} ${item.industry}`), [companies, query]);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (editId) updateCompany(editId, form);
    else addCompany(form);
    setOpen(false);
    setEditId(null);
    setForm(defaultForm);
  };

  return (
    <Card title="Companies" subtitle="Create and manage company profiles" actions={<Button onClick={() => setOpen(true)}>New Company</Button>}>
      <Input placeholder="Search company..." value={query} onChange={(event) => setQuery(event.target.value)} />
      <Table
        rows={rows}
        emptyMessage="No companies yet."
        columns={[
          { header: "Name", render: (row) => row.name },
          { header: "Industry", render: (row) => row.industry },
          { header: "Size", render: (row) => row.size },
          {
            header: "Website",
            render: (row) => (
              <a href={row.website} target="_blank" rel="noreferrer">
                {row.website}
              </a>
            ),
          },
          {
            header: "Actions",
            render: (row) => (
              <div className="row">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setEditId(row.id);
                    setForm({
                      name: row.name,
                      industry: row.industry,
                      size: row.size,
                      website: row.website,
                    });
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button variant="danger" onClick={() => deleteCompany(row.id)}>
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
          <Input placeholder="Size (e.g. 11-50)" value={form.size} onChange={(event) => setForm({ ...form, size: event.target.value })} required />
          <Input placeholder="Website" value={form.website} onChange={(event) => setForm({ ...form, website: event.target.value })} required />
          <Button type="submit">{editId ? "Save Company" : "Create Company"}</Button>
        </form>
      </Modal>
    </Card>
  );
}
