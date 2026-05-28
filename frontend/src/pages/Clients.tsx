import { useMemo, useState } from "react";
import { Badge } from "../components/Badge";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { Client } from "../types/types";
import { byQuery, currency } from "../utils/helpers";

const initialForm: Omit<Client, "id"> = {
  name: "",
  email: "",
  phone: "",
  company: "",
  industry: "",
  status: "active",
  estimatedValue: 0,
  notes: "",
  tags: [],
  lastContactDate: "",
};

export default function Clients() {
  const clients = useCRMStore((state) => state.clients);
  const contacts = useCRMStore((state) => state.contacts);
  const deals = useCRMStore((state) => state.deals);
  const activity = useCRMStore((state) => state.activity);
  const addClient = useCRMStore((state) => state.addClient);
  const updateClient = useCRMStore((state) => state.updateClient);
  const deleteClient = useCRMStore((state) => state.deleteClient);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("all");
  const [industry, setIndustry] = useState("all");
  const [open, setOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [selectedClientId, setSelectedClientId] = useState<string | null>(null);
  const [form, setForm] = useState(initialForm);
  const [tagInput, setTagInput] = useState("");

  const industries = useMemo(() => [...new Set(clients.map((client) => client.industry))], [clients]);
  const rows = useMemo(
    () =>
      byQuery(clients, query, (item) => `${item.name} ${item.email} ${item.company}`)
        .filter((item) => (status === "all" ? true : item.status === status))
        .filter((item) => (industry === "all" ? true : item.industry === industry)),
    [clients, query, status, industry]
  );

  const selectedClient = clients.find((client) => client.id === selectedClientId) ?? null;

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!/\S+@\S+\.\S+/.test(form.email)) {
      pushToast("Please provide a valid client email.", "error");
      return;
    }
    if (form.estimatedValue < 0) {
      pushToast("Estimated value must be positive.", "error");
      return;
    }
    if (editingId) {
      updateClient(editingId, form);
      pushToast("Client updated successfully.", "success");
    } else {
      addClient(form);
      pushToast("Client created successfully.", "success");
    }
    setForm(initialForm);
    setEditingId(null);
    setOpen(false);
    setTagInput("");
  };

  return (
    <Card title="Clients" subtitle="Create, edit, delete, search, filter and inspect client records" actions={<Button onClick={() => setOpen(true)}>Add Client</Button>}>
      <div className="grid cols-4">
        <Input placeholder="Search client..." value={query} onChange={(event) => setQuery(event.target.value)} />
        <Select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="all">All statuses</option>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
        </Select>
        <Select value={industry} onChange={(event) => setIndustry(event.target.value)}>
          <option value="all">All industries</option>
          {industries.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
        </Select>
      </div>

      <Table
        rows={rows}
        emptyMessage="No clients found."
        columns={[
          { header: "Name", render: (row) => row.name },
          { header: "Company", render: (row) => row.company },
          { header: "Industry", render: (row) => row.industry },
          { header: "Value", render: (row) => currency(row.estimatedValue) },
          {
            header: "Status",
            render: (row) => <Badge tone={row.status === "active" ? "success" : "warning"}>{row.status}</Badge>,
          },
          { header: "Last Contact", render: (row) => row.lastContactDate || "-" },
          {
            header: "Actions",
            render: (row) => (
              <div className="row">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setEditingId(row.id);
                    setForm({ ...row, tags: [...row.tags] });
                    setTagInput(row.tags.join(", "));
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => {
                    setSelectedClientId(row.id);
                    setDetailOpen(true);
                  }}
                >
                  View Details
                </Button>
                <Button
                  variant="danger"
                  onClick={() => {
                    if (!window.confirm("Delete this client?")) return;
                    deleteClient(row.id);
                    pushToast("Client deleted successfully.", "info");
                  }}
                >
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />

      <Modal title={editingId ? "Edit Client" : "Create Client"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={onSubmit}>
          <Input placeholder="Name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required />
          <Input placeholder="Email" type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} required />
          <Input placeholder="Phone" value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} required />
          <Input placeholder="Company" value={form.company} onChange={(event) => setForm({ ...form, company: event.target.value })} required />
          <Input placeholder="Industry" value={form.industry} onChange={(event) => setForm({ ...form, industry: event.target.value })} required />
          <Input
            type="number"
            min={0}
            placeholder="Estimated Value"
            value={form.estimatedValue}
            onChange={(event) => setForm({ ...form, estimatedValue: Number(event.target.value) })}
            required
          />
          <Input
            type="date"
            placeholder="Last Contact Date"
            value={form.lastContactDate}
            onChange={(event) => setForm({ ...form, lastContactDate: event.target.value })}
            required
          />
          <Input
            placeholder="Tags (comma separated)"
            value={tagInput}
            onChange={(event) => {
              const value = event.target.value;
              setTagInput(value);
              setForm({
                ...form,
                tags: value
                  .split(",")
                  .map((tag) => tag.trim())
                  .filter(Boolean),
              });
            }}
          />
          <Input placeholder="Notes" value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} required />
          <Select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as Client["status"] })}>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </Select>
          <Button type="submit">{editingId ? "Save" : "Create"}</Button>
        </form>
      </Modal>

      <Modal title="Client Details" open={detailOpen} onClose={() => setDetailOpen(false)}>
        {selectedClient ? (
          <div className="stack">
            <p>
              <strong>{selectedClient.name}</strong> - {selectedClient.company}
            </p>
            <p className="muted">{selectedClient.notes}</p>
            <p>Tags: {selectedClient.tags.join(", ") || "None"}</p>
            <Card title="Linked Deals">
              <Table
                rows={deals.filter((deal) => deal.clientId === selectedClient.id)}
                emptyMessage="No linked deals."
                columns={[
                  { header: "Title", render: (row) => row.title },
                  { header: "Stage", render: (row) => row.stage },
                  { header: "Value", render: (row) => currency(row.value) },
                ]}
              />
            </Card>
            <Card title="Linked Contacts">
              <Table
                rows={contacts.filter((contact) => contact.linkedClientId === selectedClient.id)}
                emptyMessage="No linked contacts."
                columns={[
                  { header: "Name", render: (row) => row.name },
                  { header: "Email", render: (row) => row.email },
                  { header: "Role", render: (row) => row.role },
                ]}
              />
            </Card>
            <Card title="Activity Timeline">
              <Table
                rows={activity.filter((item) => item.entity === "client" && item.entityId === selectedClient.id).slice(0, 8)}
                emptyMessage="No timeline entries yet."
                columns={[
                  { header: "Time", render: (row) => new Date(row.createdAt).toLocaleString() },
                  { header: "Activity", render: (row) => row.text },
                ]}
              />
            </Card>
          </div>
        ) : (
          <p className="muted">No client selected.</p>
        )}
      </Modal>
    </Card>
  );
}
