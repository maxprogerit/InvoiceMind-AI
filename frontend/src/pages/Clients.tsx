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
import { byQuery } from "../utils/helpers";

const initialForm: Omit<Client, "id"> = { name: "", email: "", phone: "", status: "active" };

export default function Clients() {
  const clients = useCRMStore((state) => state.clients);
  const addClient = useCRMStore((state) => state.addClient);
  const updateClient = useCRMStore((state) => state.updateClient);
  const deleteClient = useCRMStore((state) => state.deleteClient);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("all");
  const [open, setOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState(initialForm);

  const rows = useMemo(
    () =>
      byQuery(clients, query, (item) => `${item.name} ${item.email}`)
        .filter((item) => (status === "all" ? true : item.status === status)),
    [clients, query, status]
  );

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    if (editingId) {
      updateClient(editingId, form);
      pushToast("Client updated.", "success");
    } else {
      addClient(form);
      pushToast("Client added.", "success");
    }
    setForm(initialForm);
    setEditingId(null);
    setOpen(false);
  };

  return (
    <Card
      title="Clients"
      subtitle="Search, filter, create and edit clients"
      actions={<Button onClick={() => setOpen(true)}>New Client</Button>}
    >
      <div className="grid cols-4">
        <Input placeholder="Search client..." value={query} onChange={(event) => setQuery(event.target.value)} />
        <Select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="all">All statuses</option>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
        </Select>
      </div>

      <Table
        rows={rows}
        emptyMessage="No clients yet. Create your first client."
        columns={[
          { header: "Name", render: (row) => row.name },
          { header: "Email", render: (row) => row.email },
          { header: "Phone", render: (row) => row.phone },
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
                    setEditingId(row.id);
                    setForm({ name: row.name, email: row.email, phone: row.phone, status: row.status });
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button
                  variant="danger"
                  onClick={() => {
                    deleteClient(row.id);
                    pushToast("Client deleted.", "info");
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
          <Select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as Client["status"] })}>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </Select>
          <Button type="submit">{editingId ? "Save" : "Create"}</Button>
        </form>
      </Modal>
    </Card>
  );
}
