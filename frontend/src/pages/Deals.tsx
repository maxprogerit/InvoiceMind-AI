import { useMemo, useState } from "react";
import { Badge } from "../components/Badge";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { Deal } from "../types/types";
import { byQuery, currency } from "../utils/helpers";

const defaultForm: Omit<Deal, "id"> = {
  title: "",
  clientId: "",
  value: 0,
  stage: "lead",
  closeDate: "",
};

export default function Deals() {
  const deals = useCRMStore((state) => state.deals);
  const clients = useCRMStore((state) => state.clients);
  const addDeal = useCRMStore((state) => state.addDeal);
  const updateDeal = useCRMStore((state) => state.updateDeal);
  const deleteDeal = useCRMStore((state) => state.deleteDeal);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [query, setQuery] = useState("");
  const [stageFilter, setStageFilter] = useState("all");
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [form, setForm] = useState<Omit<Deal, "id">>(defaultForm);

  const rows = useMemo(
    () =>
      byQuery(deals, query, (deal) => deal.title).filter((deal) =>
        stageFilter === "all" ? true : deal.stage === stageFilter
      ),
    [deals, query, stageFilter]
  );

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (editId) {
      updateDeal(editId, form);
      pushToast("Deal updated.", "success");
    } else {
      addDeal(form);
      pushToast("Deal created.", "success");
    }
    setOpen(false);
    setEditId(null);
    setForm(defaultForm);
  };

  return (
    <Card title="Deals" subtitle="Pipeline management and deal CRUD" actions={<Button onClick={() => setOpen(true)}>New Deal</Button>}>
      <div className="grid cols-4">
        <Input placeholder="Search deal..." value={query} onChange={(event) => setQuery(event.target.value)} />
        <Select value={stageFilter} onChange={(event) => setStageFilter(event.target.value)}>
          <option value="all">All stages</option>
          <option value="lead">Lead</option>
          <option value="qualified">Qualified</option>
          <option value="proposal">Proposal</option>
          <option value="won">Won</option>
          <option value="lost">Lost</option>
        </Select>
      </div>

      <Table
        rows={rows}
        emptyMessage="No deals yet."
        columns={[
          { header: "Title", render: (row) => row.title },
          {
            header: "Client",
            render: (row) => clients.find((client) => client.id === row.clientId)?.name ?? "Unknown",
          },
          { header: "Value", render: (row) => currency(row.value) },
          {
            header: "Stage",
            render: (row) => (
              <Badge tone={row.stage === "won" ? "success" : row.stage === "lost" ? "danger" : "default"}>{row.stage}</Badge>
            ),
          },
          { header: "Close Date", render: (row) => row.closeDate },
          {
            header: "Actions",
            render: (row) => (
              <div className="row">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setEditId(row.id);
                    setForm({
                      title: row.title,
                      clientId: row.clientId,
                      value: row.value,
                      stage: row.stage,
                      closeDate: row.closeDate,
                    });
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button variant="danger" onClick={() => deleteDeal(row.id)}>
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />

      <Modal title={editId ? "Edit Deal" : "Create Deal"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Input placeholder="Title" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} required />
          <Select value={form.clientId} onChange={(event) => setForm({ ...form, clientId: event.target.value })} required>
            <option value="">Select client</option>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
          </Select>
          <Input type="number" min={1} placeholder="Value" value={form.value} onChange={(event) => setForm({ ...form, value: Number(event.target.value) })} required />
          <Select value={form.stage} onChange={(event) => setForm({ ...form, stage: event.target.value as Deal["stage"] })}>
            <option value="lead">Lead</option>
            <option value="qualified">Qualified</option>
            <option value="proposal">Proposal</option>
            <option value="won">Won</option>
            <option value="lost">Lost</option>
          </Select>
          <Input type="date" value={form.closeDate} onChange={(event) => setForm({ ...form, closeDate: event.target.value })} required />
          <Button type="submit">{editId ? "Save Deal" : "Create Deal"}</Button>
        </form>
      </Modal>
    </Card>
  );
}

