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
  company: "",
  stage: "New",
  value: 0,
  probability: 10,
  expectedCloseDate: "",
  assignedTeamMemberId: "",
  notes: "",
};

const stages: Deal["stage"][] = ["New", "Qualified", "Proposal", "Negotiation", "Won", "Lost"];

export default function Deals() {
  const deals = useCRMStore((state) => state.deals);
  const clients = useCRMStore((state) => state.clients);
  const team = useCRMStore((state) => state.team);
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
      byQuery(deals, query, (deal) => `${deal.title} ${deal.company}`).filter((deal) =>
        stageFilter === "all" ? true : deal.stage === stageFilter
      ),
    [deals, query, stageFilter]
  );

  const pipelineValue = deals.reduce((sum, deal) => sum + deal.value, 0);
  const wonDeals = deals.filter((deal) => deal.stage === "Won");
  const closedDeals = deals.filter((deal) => deal.stage === "Won" || deal.stage === "Lost");
  const winRate = closedDeals.length ? (wonDeals.length / closedDeals.length) * 100 : 0;
  const forecastRevenue = deals.reduce((sum, deal) => sum + (deal.value * deal.probability) / 100, 0);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!form.clientId) {
      pushToast("Client is required for a deal.", "error");
      return;
    }
    if (form.value <= 0) {
      pushToast("Deal value must be positive.", "error");
      return;
    }
    if (form.probability < 0 || form.probability > 100) {
      pushToast("Probability must be between 0 and 100.", "error");
      return;
    }
    if (editId) {
      updateDeal(editId, form);
      pushToast(`Deal moved to ${form.stage}.`, "success");
    } else {
      addDeal(form);
      pushToast("Deal created successfully.", "success");
    }
    setOpen(false);
    setEditId(null);
    setForm(defaultForm);
  };

  return (
    <div className="stack">
      <div className="grid cols-4">
        <Card title="Pipeline Value">
          <h2>{currency(pipelineValue)}</h2>
        </Card>
        <Card title="Won Deals">
          <h2>{wonDeals.length}</h2>
        </Card>
        <Card title="Win Rate">
          <h2>{winRate.toFixed(1)}%</h2>
        </Card>
        <Card title="Forecast Revenue">
          <h2>{currency(forecastRevenue)}</h2>
        </Card>
      </div>

      <Card title="Deals" subtitle="Create, edit, delete, search, filter and move deals by stage" actions={<Button onClick={() => setOpen(true)}>Add Deal</Button>}>
        <div className="grid cols-4">
          <Input placeholder="Search deal..." value={query} onChange={(event) => setQuery(event.target.value)} />
          <Select value={stageFilter} onChange={(event) => setStageFilter(event.target.value)}>
            <option value="all">All stages</option>
            {stages.map((stage) => (
              <option key={stage} value={stage}>
                {stage}
              </option>
            ))}
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
            { header: "Company", render: (row) => row.company },
            { header: "Value", render: (row) => currency(row.value) },
            { header: "Probability", render: (row) => `${row.probability}%` },
            { header: "Close Date", render: (row) => row.expectedCloseDate },
            {
              header: "Stage",
              render: (row) => (
                <Select
                  value={row.stage}
                  onChange={(event) => {
                    updateDeal(row.id, { ...row, stage: event.target.value as Deal["stage"] });
                    pushToast(`Deal moved to ${event.target.value}.`, "success");
                  }}
                >
                  {stages.map((stage) => (
                    <option key={stage} value={stage}>
                      {stage}
                    </option>
                  ))}
                </Select>
              ),
            },
            {
              header: "Status",
              render: (row) => (
                <Badge tone={row.stage === "Won" ? "success" : row.stage === "Lost" ? "danger" : "default"}>{row.stage}</Badge>
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
                      setForm({ ...row });
                      setOpen(true);
                    }}
                  >
                    Edit
                  </Button>
                  <Button
                    variant="danger"
                    onClick={() => {
                      if (!window.confirm("Delete this deal?")) return;
                      deleteDeal(row.id);
                      pushToast("Deal deleted.", "info");
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
          <Input placeholder="Company" value={form.company} onChange={(event) => setForm({ ...form, company: event.target.value })} required />
          <Input
            type="number"
            min={1}
            placeholder="Value"
            value={form.value}
            onChange={(event) => setForm({ ...form, value: Number(event.target.value) })}
            required
          />
          <Input
            type="number"
            min={0}
            max={100}
            placeholder="Probability"
            value={form.probability}
            onChange={(event) => setForm({ ...form, probability: Number(event.target.value) })}
            required
          />
          <Input type="date" value={form.expectedCloseDate} onChange={(event) => setForm({ ...form, expectedCloseDate: event.target.value })} required />
          <Select value={form.assignedTeamMemberId} onChange={(event) => setForm({ ...form, assignedTeamMemberId: event.target.value })}>
            <option value="">Assigned team member</option>
            {team.map((member) => (
              <option key={member.id} value={member.id}>
                {member.name}
              </option>
            ))}
          </Select>
          <Select value={form.stage} onChange={(event) => setForm({ ...form, stage: event.target.value as Deal["stage"] })}>
            {stages.map((stage) => (
              <option key={stage} value={stage}>
                {stage}
              </option>
            ))}
          </Select>
          <Input placeholder="Notes" value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} required />
          <Button type="submit">{editId ? "Save Deal" : "Create Deal"}</Button>
        </form>
      </Modal>
    </div>
  );
}
