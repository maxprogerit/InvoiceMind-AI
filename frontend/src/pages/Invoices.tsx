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
  invoiceNumber: "",
  clientId: "",
  items: [],
  subtotal: 0,
  tax: 0,
  total: 0,
  dueDate: "",
  issuedDate: "",
  status: "Draft",
  notes: "",
};

export default function Invoices() {
  const clients = useCRMStore((state) => state.clients);
  const invoices = useCRMStore((state) => state.invoices);
  const addInvoice = useCRMStore((state) => state.addInvoice);
  const updateInvoice = useCRMStore((state) => state.updateInvoice);
  const deleteInvoice = useCRMStore((state) => state.deleteInvoice);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [open, setOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewId, setPreviewId] = useState<string | null>(null);
  const [editId, setEditId] = useState<string | null>(null);
  const [status, setStatus] = useState("all");
  const [form, setForm] = useState(defaultForm);
  const [itemDescription, setItemDescription] = useState("");
  const [itemQuantity, setItemQuantity] = useState(1);
  const [itemPrice, setItemPrice] = useState(0);

  const rows = useMemo(
    () => invoices.filter((invoice) => (status === "all" ? true : invoice.status === status)),
    [invoices, status]
  );

  const selectedInvoice = invoices.find((invoice) => invoice.id === previewId) ?? null;

  const recalc = (draft: Omit<Invoice, "id">) => {
    const subtotal = draft.items.reduce((sum, item) => sum + item.quantity * item.price, 0);
    const total = subtotal + subtotal * (draft.tax / 100);
    return { ...draft, subtotal, total };
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!form.clientId) {
      pushToast("Invoice must be linked to a client.", "error");
      return;
    }
    if (!form.items.length) {
      pushToast("Invoice items cannot be empty.", "error");
      return;
    }
    if (form.tax < 0) {
      pushToast("Tax must be positive.", "error");
      return;
    }
    const payload = recalc(form);
    if (editId) {
      updateInvoice(editId, payload);
      pushToast("Invoice updated successfully.", "success");
    } else {
      addInvoice(payload);
      pushToast("Invoice created successfully.", "success");
    }
    setEditId(null);
    setOpen(false);
    setForm(defaultForm);
  };

  return (
    <Card title="Invoices" subtitle="Create, edit, delete, preview and update invoice statuses" actions={<Button onClick={() => setOpen(true)}>Create Invoice</Button>}>
      <div className="row">
        <Select value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="all">All statuses</option>
          <option value="Draft">Draft</option>
          <option value="Pending">Pending</option>
          <option value="Paid">Paid</option>
          <option value="Overdue">Overdue</option>
        </Select>
      </div>
      <Table
        rows={rows}
        emptyMessage="No invoices yet."
        columns={[
          { header: "Invoice", render: (row) => row.invoiceNumber },
          { header: "Client", render: (row) => clients.find((client) => client.id === row.clientId)?.name ?? "Unknown" },
          { header: "Subtotal", render: (row) => currency(row.subtotal) },
          { header: "Tax", render: (row) => `${row.tax}%` },
          { header: "Total", render: (row) => currency(row.total) },
          { header: "Due", render: (row) => row.dueDate },
          {
            header: "Status",
            render: (row) => (
              <Select
                value={row.status}
                onChange={(event) => {
                  updateInvoice(row.id, { ...row, status: event.target.value as Invoice["status"] });
                  pushToast(`Invoice marked as ${event.target.value}.`, "success");
                }}
              >
                <option value="Draft">Draft</option>
                <option value="Pending">Pending</option>
                <option value="Paid">Paid</option>
                <option value="Overdue">Overdue</option>
              </Select>
            ),
          },
          {
            header: "Badge",
            render: (row) => (
              <Badge tone={row.status === "Paid" ? "success" : row.status === "Overdue" ? "danger" : "default"}>{row.status}</Badge>
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
                    setForm({ ...row, items: [...row.items] });
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => {
                    setPreviewId(row.id);
                    setPreviewOpen(true);
                  }}
                >
                  View Details
                </Button>
                <Button variant="secondary" onClick={() => pushToast(`Invoice ${row.invoiceNumber} sent.`, "success")}>
                  Send Invoice
                </Button>
                <Button variant="secondary" onClick={() => pushToast(`PDF generated for ${row.invoiceNumber}.`, "success")}>
                  Download PDF
                </Button>
                <Button
                  variant="danger"
                  onClick={() => {
                    if (!window.confirm("Delete this invoice?")) return;
                    deleteInvoice(row.id);
                    pushToast("Invoice deleted.", "info");
                  }}
                >
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />

      <Modal title={editId ? "Edit Invoice" : "Create Invoice"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Input
            placeholder="Invoice number"
            value={form.invoiceNumber}
            onChange={(event) => setForm({ ...form, invoiceNumber: event.target.value })}
            required
          />
          <Select value={form.clientId} onChange={(event) => setForm({ ...form, clientId: event.target.value })} required>
            <option value="">Select client</option>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
          </Select>
          <div className="row">
            <Input placeholder="Item description" value={itemDescription} onChange={(event) => setItemDescription(event.target.value)} />
            <Input type="number" min={1} value={itemQuantity} onChange={(event) => setItemQuantity(Number(event.target.value))} />
            <Input type="number" min={0} value={itemPrice} onChange={(event) => setItemPrice(Number(event.target.value))} />
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                if (!itemDescription.trim() || itemQuantity <= 0 || itemPrice <= 0) {
                  pushToast("Invoice item requires description, quantity and positive price.", "error");
                  return;
                }
                setForm((current) =>
                  recalc({
                    ...current,
                    items: [
                      ...current.items,
                      { id: `${Date.now()}`, description: itemDescription.trim(), quantity: itemQuantity, price: itemPrice },
                    ],
                  })
                );
                setItemDescription("");
                setItemQuantity(1);
                setItemPrice(0);
              }}
            >
              Add Item
            </Button>
          </div>
          <Table
            rows={form.items}
            emptyMessage="No invoice items yet."
            columns={[
              { header: "Description", render: (row) => row.description },
              { header: "Qty", render: (row) => row.quantity },
              { header: "Price", render: (row) => currency(row.price) },
              { header: "Line Total", render: (row) => currency(row.quantity * row.price) },
            ]}
          />
          <Input
            type="number"
            min={0}
            placeholder="Tax %"
            value={form.tax}
            onChange={(event) => setForm(recalc({ ...form, tax: Number(event.target.value) }))}
            required
          />
          <Input type="date" value={form.issuedDate} onChange={(event) => setForm({ ...form, issuedDate: event.target.value })} required />
          <Input type="date" value={form.dueDate} onChange={(event) => setForm({ ...form, dueDate: event.target.value })} required />
          <Select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as Invoice["status"] })}>
            <option value="Draft">Draft</option>
            <option value="Pending">Pending</option>
            <option value="Paid">Paid</option>
            <option value="Overdue">Overdue</option>
          </Select>
          <Input placeholder="Notes" value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} />
          <p className="muted">
            Subtotal: {currency(form.subtotal)} | Total: {currency(form.total)}
          </p>
          <Button type="submit">{editId ? "Save Invoice" : "Create Invoice"}</Button>
        </form>
      </Modal>

      <Modal title="Invoice Preview" open={previewOpen} onClose={() => setPreviewOpen(false)}>
        {selectedInvoice ? (
          <div className="stack">
            <h3>{selectedInvoice.invoiceNumber}</h3>
            <p>Client: {clients.find((client) => client.id === selectedInvoice.clientId)?.name ?? "Unknown"}</p>
            <p>
              Issued: {selectedInvoice.issuedDate} | Due: {selectedInvoice.dueDate}
            </p>
            <p>Status: {selectedInvoice.status}</p>
            <Table
              rows={selectedInvoice.items}
              emptyMessage="No items."
              columns={[
                { header: "Item", render: (row) => row.description },
                { header: "Qty", render: (row) => row.quantity },
                { header: "Price", render: (row) => currency(row.price) },
              ]}
            />
            <p>
              Subtotal: {currency(selectedInvoice.subtotal)} | Tax: {selectedInvoice.tax}% | Total: {currency(selectedInvoice.total)}
            </p>
            <p className="muted">{selectedInvoice.notes}</p>
          </div>
        ) : null}
      </Modal>
    </Card>
  );
}
