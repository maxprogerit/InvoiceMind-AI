import { useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { Receipt, ReceiptCategory } from "../types/types";
import { currency } from "../utils/helpers";

const defaultForm: Omit<Receipt, "id"> = {
  receiptNumber: "",
  vendor: "",
  amount: 0,
  category: "Other",
  date: "",
  paymentMethod: "Card",
  linkedClientId: "",
  linkedDealId: "",
  attachment: "",
  notes: "",
};

const categories: ReceiptCategory[] = ["Travel", "Software", "Office", "Marketing", "Operations", "Other"];

export default function Receipts() {
  const receipts = useCRMStore((state) => state.receipts);
  const clients = useCRMStore((state) => state.clients);
  const deals = useCRMStore((state) => state.deals);
  const addReceipt = useCRMStore((state) => state.addReceipt);
  const updateReceipt = useCRMStore((state) => state.updateReceipt);
  const deleteReceipt = useCRMStore((state) => state.deleteReceipt);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [open, setOpen] = useState(false);
  const [previewId, setPreviewId] = useState<string | null>(null);
  const [editId, setEditId] = useState<string | null>(null);
  const [form, setForm] = useState(defaultForm);
  const [categoryFilter, setCategoryFilter] = useState("all");

  const filteredRows = useMemo(
    () => receipts.filter((receipt) => (categoryFilter === "all" ? true : receipt.category === categoryFilter)),
    [receipts, categoryFilter]
  );

  const selectedReceipt = receipts.find((receipt) => receipt.id === previewId) ?? null;
  const totalExpense = filteredRows.reduce((sum, receipt) => sum + receipt.amount, 0);
  const monthlyData = Object.values(
    filteredRows.reduce<Record<string, { month: string; amount: number }>>((acc, receipt) => {
      const month = receipt.date.slice(0, 7);
      acc[month] = acc[month] || { month, amount: 0 };
      acc[month].amount += receipt.amount;
      return acc;
    }, {})
  );

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (form.amount <= 0) {
      pushToast("Receipt amount must be positive.", "error");
      return;
    }
    if (editId) {
      updateReceipt(editId, form);
      pushToast("Receipt updated successfully.", "success");
    } else {
      addReceipt(form);
      pushToast("Receipt created successfully.", "success");
    }
    setEditId(null);
    setOpen(false);
    setForm(defaultForm);
  };

  return (
    <div className="stack">
      <div className="grid cols-2">
        <Card title="Expense Summary">
          <h2>{currency(totalExpense)}</h2>
        </Card>
        <Card title="Monthly Expense Chart">
          <div style={{ width: "100%", height: 260 }}>
            <ResponsiveContainer>
              <BarChart data={monthlyData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="amount" fill="#ef4444" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      <Card title="Receipts" subtitle="Create, upload attachment, edit, delete and filter expenses" actions={<Button onClick={() => setOpen(true)}>Create Receipt</Button>}>
        <div className="row">
          <Select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}>
            <option value="all">All categories</option>
            {categories.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </Select>
        </div>
        <Table
          rows={filteredRows}
          emptyMessage="No receipts yet."
          columns={[
            { header: "Receipt", render: (row) => row.receiptNumber },
            { header: "Vendor", render: (row) => row.vendor },
            { header: "Amount", render: (row) => currency(row.amount) },
            { header: "Category", render: (row) => row.category },
            { header: "Date", render: (row) => row.date },
            { header: "Payment", render: (row) => row.paymentMethod },
            {
              header: "Linked Client",
              render: (row) => clients.find((client) => client.id === row.linkedClientId)?.name ?? "-",
            },
            {
              header: "Linked Deal",
              render: (row) => deals.find((deal) => deal.id === row.linkedDealId)?.title ?? "-",
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
                  <Button variant="secondary" onClick={() => setPreviewId(row.id)}>
                    View Details
                  </Button>
                  <Button
                    variant="danger"
                    onClick={() => {
                      if (!window.confirm("Delete this receipt?")) return;
                      deleteReceipt(row.id);
                      pushToast("Receipt deleted.", "info");
                    }}
                  >
                    Delete
                  </Button>
                </div>
              ),
            },
          ]}
        />
      </Card>

      <Modal title={editId ? "Edit Receipt" : "Create Receipt"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Input placeholder="Receipt number" value={form.receiptNumber} onChange={(event) => setForm({ ...form, receiptNumber: event.target.value })} required />
          <Input placeholder="Vendor" value={form.vendor} onChange={(event) => setForm({ ...form, vendor: event.target.value })} required />
          <Input type="number" min={1} value={form.amount} onChange={(event) => setForm({ ...form, amount: Number(event.target.value) })} required />
          <Select value={form.category} onChange={(event) => setForm({ ...form, category: event.target.value as Receipt["category"] })}>
            {categories.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </Select>
          <Input type="date" value={form.date} onChange={(event) => setForm({ ...form, date: event.target.value })} required />
          <Input placeholder="Payment method" value={form.paymentMethod} onChange={(event) => setForm({ ...form, paymentMethod: event.target.value })} required />
          <Select value={form.linkedClientId} onChange={(event) => setForm({ ...form, linkedClientId: event.target.value })}>
            <option value="">Linked client</option>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
          </Select>
          <Select value={form.linkedDealId} onChange={(event) => setForm({ ...form, linkedDealId: event.target.value })}>
            <option value="">Linked deal</option>
            {deals.map((deal) => (
              <option key={deal.id} value={deal.id}>
                {deal.title}
              </option>
            ))}
          </Select>
          <Input placeholder="Attachment file name" value={form.attachment ?? ""} onChange={(event) => setForm({ ...form, attachment: event.target.value })} />
          <Input placeholder="Notes" value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} />
          <Button type="submit">{editId ? "Save Receipt" : "Create Receipt"}</Button>
        </form>
      </Modal>

      <Modal title="Receipt Preview" open={!!selectedReceipt} onClose={() => setPreviewId(null)}>
        {selectedReceipt ? (
          <div className="stack">
            <h3>{selectedReceipt.receiptNumber}</h3>
            <p>
              {selectedReceipt.vendor} - {currency(selectedReceipt.amount)} ({selectedReceipt.category})
            </p>
            <p>
              Date: {selectedReceipt.date} | Payment: {selectedReceipt.paymentMethod}
            </p>
            <p>Attachment: {selectedReceipt.attachment || "No attachment"}</p>
            <p className="muted">{selectedReceipt.notes}</p>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
