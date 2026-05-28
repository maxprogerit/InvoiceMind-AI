import { useState } from "react";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { Receipt } from "../types/types";
import { currency } from "../utils/helpers";

const defaultForm: Omit<Receipt, "id"> = {
  invoiceId: "",
  amount: 0,
  date: "",
  method: "Bank Transfer",
};

export default function Receipts() {
  const receipts = useCRMStore((state) => state.receipts);
  const invoices = useCRMStore((state) => state.invoices);
  const addReceipt = useCRMStore((state) => state.addReceipt);
  const updateReceipt = useCRMStore((state) => state.updateReceipt);
  const deleteReceipt = useCRMStore((state) => state.deleteReceipt);
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [form, setForm] = useState(defaultForm);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (editId) updateReceipt(editId, form);
    else addReceipt(form);
    setEditId(null);
    setOpen(false);
    setForm(defaultForm);
  };

  return (
    <Card title="Receipts" subtitle="Receipt creation and tracking" actions={<Button onClick={() => setOpen(true)}>Create Receipt</Button>}>
      <Table
        rows={receipts}
        emptyMessage="No receipts yet."
        columns={[
          { header: "Receipt", render: (row) => row.id },
          { header: "Invoice", render: (row) => row.invoiceId },
          { header: "Amount", render: (row) => currency(row.amount) },
          { header: "Date", render: (row) => row.date },
          { header: "Method", render: (row) => row.method },
          {
            header: "Actions",
            render: (row) => (
              <div className="row">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setEditId(row.id);
                    setForm({
                      invoiceId: row.invoiceId,
                      amount: row.amount,
                      date: row.date,
                      method: row.method,
                    });
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button variant="danger" onClick={() => deleteReceipt(row.id)}>
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />

      <Modal title={editId ? "Edit Receipt" : "Create Receipt"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Select value={form.invoiceId} onChange={(event) => setForm({ ...form, invoiceId: event.target.value })} required>
            <option value="">Select invoice</option>
            {invoices.map((invoice) => (
              <option key={invoice.id} value={invoice.id}>
                {invoice.id}
              </option>
            ))}
          </Select>
          <Input type="number" min={1} value={form.amount} onChange={(event) => setForm({ ...form, amount: Number(event.target.value) })} required />
          <Input type="date" value={form.date} onChange={(event) => setForm({ ...form, date: event.target.value })} required />
          <Input placeholder="Method" value={form.method} onChange={(event) => setForm({ ...form, method: event.target.value })} required />
          <Button type="submit">{editId ? "Save Receipt" : "Create Receipt"}</Button>
        </form>
      </Modal>
    </Card>
  );
}
