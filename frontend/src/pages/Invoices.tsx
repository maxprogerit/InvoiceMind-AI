import { useMemo, useState } from "react";
import { Badge } from "../components/Badge";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { Invoice } from "../types/types";
import { currency } from "../utils/helpers";

const defaultForm: Omit<Invoice, "id"> = {
  clientId: "",
  amount: 0,
  dueDate: "",
  issuedDate: "",
  status: "draft",
};

export default function Invoices() {
  const clients = useCRMStore((state) => state.clients);
  const invoices = useCRMStore((state) => state.invoices);
  const addInvoice = useCRMStore((state) => state.addInvoice);
  const updateInvoice = useCRMStore((state) => state.updateInvoice);
  const deleteInvoice = useCRMStore((state) => state.deleteInvoice);
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [status, setStatus] = useState("all");
  const [form, setForm] = useState(defaultForm);

  const rows = useMemo(
    () => invoices.filter((invoice) => (status === "all" ? true : invoice.status === status)),
    [invoices, status]
  );

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (editId) updateInvoice(editId, form);
    else addInvoice(form);
    setEditId(null);
    setOpen(false);
    setForm(defaultForm);
  };

  return (
    <Card title="Invoices" subtitle="Invoice creation and CRUD" actions={<Button onClick={() => setOpen(true)}>Create Invoice</Button>}>
      <div className="row">
        <Select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="all">All statuses</option>
          <option value="draft">Draft</option>
          <option value="sent">Sent</option>
          <option value="paid">Paid</option>
          <option value="overdue">Overdue</option>
        </Select>
      </div>
      <Table
        rows={rows}
        emptyMessage="No invoices yet."
        columns={[
          { header: "Invoice", render: (row) => row.id },
          { header: "Client", render: (row) => clients.find((client) => client.id === row.clientId)?.name ?? "Unknown" },
          { header: "Amount", render: (row) => currency(row.amount) },
          { header: "Issued", render: (row) => row.issuedDate },
          { header: "Due", render: (row) => row.dueDate },
          {
            header: "Status",
            render: (row) => (
              <Badge tone={row.status === "paid" ? "success" : row.status === "overdue" ? "danger" : "default"}>{row.status}</Badge>
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
                      clientId: row.clientId,
                      amount: row.amount,
                      dueDate: row.dueDate,
                      issuedDate: row.issuedDate,
                      status: row.status,
                    });
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button variant="danger" onClick={() => deleteInvoice(row.id)}>
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />

      <Modal title={editId ? "Edit Invoice" : "Create Invoice"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Select value={form.clientId} onChange={(event) => setForm({ ...form, clientId: event.target.value })} required>
            <option value="">Select client</option>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
          </Select>
          <Input type="number" value={form.amount} min={1} onChange={(event) => setForm({ ...form, amount: Number(event.target.value) })} required />
          <Input type="date" value={form.issuedDate} onChange={(event) => setForm({ ...form, issuedDate: event.target.value })} required />
          <Input type="date" value={form.dueDate} onChange={(event) => setForm({ ...form, dueDate: event.target.value })} required />
          <Select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as Invoice["status"] })}>
            <option value="draft">Draft</option>
            <option value="sent">Sent</option>
            <option value="paid">Paid</option>
            <option value="overdue">Overdue</option>
          </Select>
          <Button type="submit">{editId ? "Save Invoice" : "Create Invoice"}</Button>
        </form>
      </Modal>
    </Card>
  );
}
