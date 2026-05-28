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
  firstName: "",
  lastName: "",
  email: "",
  phone: "",
  companyId: "",
};

export default function Contacts() {
  const contacts = useCRMStore((state) => state.contacts);
  const companies = useCRMStore((state) => state.companies);
  const addContact = useCRMStore((state) => state.addContact);
  const updateContact = useCRMStore((state) => state.updateContact);
  const deleteContact = useCRMStore((state) => state.deleteContact);
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [form, setForm] = useState(defaultForm);

  const rows = useMemo(
    () => byQuery(contacts, query, (item) => `${item.firstName} ${item.lastName} ${item.email}`),
    [contacts, query]
  );

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (editId) {
      updateContact(editId, form);
    } else {
      addContact(form);
    }
    setOpen(false);
    setEditId(null);
    setForm(defaultForm);
  };

  return (
    <Card title="Contacts" subtitle="Keep all contact records in one place" actions={<Button onClick={() => setOpen(true)}>New Contact</Button>}>
      <Input placeholder="Search contact..." value={query} onChange={(event) => setQuery(event.target.value)} />
      <Table
        rows={rows}
        emptyMessage="No contacts yet."
        columns={[
          { header: "Name", render: (row) => `${row.firstName} ${row.lastName}` },
          { header: "Email", render: (row) => row.email },
          { header: "Phone", render: (row) => row.phone },
          {
            header: "Company",
            render: (row) => companies.find((company) => company.id === row.companyId)?.name ?? "Unknown",
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
                      firstName: row.firstName,
                      lastName: row.lastName,
                      email: row.email,
                      phone: row.phone,
                      companyId: row.companyId,
                    });
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button variant="danger" onClick={() => deleteContact(row.id)}>
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />
      <Modal title={editId ? "Edit Contact" : "Create Contact"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Input placeholder="First name" value={form.firstName} onChange={(event) => setForm({ ...form, firstName: event.target.value })} required />
          <Input placeholder="Last name" value={form.lastName} onChange={(event) => setForm({ ...form, lastName: event.target.value })} required />
          <Input placeholder="Email" type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} required />
          <Input placeholder="Phone" value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} required />
          <Select value={form.companyId} onChange={(event) => setForm({ ...form, companyId: event.target.value })} required>
            <option value="">Select company</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name}
              </option>
            ))}
          </Select>
          <Button type="submit">{editId ? "Save Contact" : "Create Contact"}</Button>
        </form>
      </Modal>
    </Card>
  );
}
