import { useMemo, useState } from "react";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { Contact } from "../types/types";
import { byQuery } from "../utils/helpers";

const defaultForm: Omit<Contact, "id"> = {
  name: "",
  email: "",
  phone: "",
  role: "",
  companyId: "",
  linkedClientId: "",
  communicationHistory: [],
  notes: "",
  lastCommunication: "",
};

export default function Contacts() {
  const contacts = useCRMStore((state) => state.contacts);
  const companies = useCRMStore((state) => state.companies);
  const clients = useCRMStore((state) => state.clients);
  const addContact = useCRMStore((state) => state.addContact);
  const updateContact = useCRMStore((state) => state.updateContact);
  const deleteContact = useCRMStore((state) => state.deleteContact);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [companyFilter, setCompanyFilter] = useState("all");
  const [form, setForm] = useState(defaultForm);
  const [historyInput, setHistoryInput] = useState("");

  const rows = useMemo(
    () =>
      byQuery(contacts, query, (item) => `${item.name} ${item.email} ${item.role}`)
        .filter((item) => (companyFilter === "all" ? true : item.companyId === companyFilter)),
    [contacts, query, companyFilter]
  );

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!/\S+@\S+\.\S+/.test(form.email)) {
      pushToast("Please provide a valid contact email.", "error");
      return;
    }
    if (editId) {
      updateContact(editId, form);
      pushToast("Contact updated successfully.", "success");
    } else {
      addContact(form);
      pushToast("Contact created successfully.", "success");
    }
    setOpen(false);
    setEditId(null);
    setForm(defaultForm);
    setHistoryInput("");
  };

  return (
    <Card title="Contacts" subtitle="Manage linked contacts, communication history and notes" actions={<Button onClick={() => setOpen(true)}>Add Contact</Button>}>
      <div className="grid cols-4">
        <Input placeholder="Search contact..." value={query} onChange={(event) => setQuery(event.target.value)} />
        <Select value={companyFilter} onChange={(event) => setCompanyFilter(event.target.value)}>
          <option value="all">All companies</option>
          {companies.map((company) => (
            <option key={company.id} value={company.id}>
              {company.name}
            </option>
          ))}
        </Select>
      </div>
      <Table
        rows={rows}
        emptyMessage="No contacts yet."
        columns={[
          { header: "Name", render: (row) => row.name },
          { header: "Email", render: (row) => row.email },
          { header: "Phone", render: (row) => row.phone },
          { header: "Role", render: (row) => row.role },
          {
            header: "Company",
            render: (row) => companies.find((company) => company.id === row.companyId)?.name ?? "Unknown",
          },
          {
            header: "Linked Client",
            render: (row) => clients.find((client) => client.id === row.linkedClientId)?.name ?? "-",
          },
          { header: "Last Communication", render: (row) => row.lastCommunication || "-" },
          {
            header: "Actions",
            render: (row) => (
              <div className="row">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setEditId(row.id);
                    setForm({ ...row, communicationHistory: [...row.communicationHistory] });
                    setHistoryInput(row.communicationHistory.join(" | "));
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button
                  variant="danger"
                  onClick={() => {
                    if (!window.confirm("Delete this contact?")) return;
                    deleteContact(row.id);
                    pushToast("Contact deleted.", "info");
                  }}
                >
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />
      <Modal title={editId ? "Edit Contact" : "Create Contact"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Input placeholder="Name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required />
          <Input placeholder="Email" type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} required />
          <Input placeholder="Phone" value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} required />
          <Input placeholder="Role" value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })} required />
          <Select value={form.companyId} onChange={(event) => setForm({ ...form, companyId: event.target.value })} required>
            <option value="">Select company</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name}
              </option>
            ))}
          </Select>
          <Select value={form.linkedClientId} onChange={(event) => setForm({ ...form, linkedClientId: event.target.value })} required>
            <option value="">Select linked client</option>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
          </Select>
          <Input
            type="date"
            value={form.lastCommunication}
            onChange={(event) => setForm({ ...form, lastCommunication: event.target.value })}
            required
          />
          <Input
            placeholder="Communication history (use | between entries)"
            value={historyInput}
            onChange={(event) => {
              const value = event.target.value;
              setHistoryInput(value);
              setForm({ ...form, communicationHistory: value.split("|").map((item) => item.trim()).filter(Boolean) });
            }}
          />
          <Input placeholder="Notes" value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} required />
          <Button type="submit">{editId ? "Save Contact" : "Create Contact"}</Button>
        </form>
      </Modal>
    </Card>
  );
}
