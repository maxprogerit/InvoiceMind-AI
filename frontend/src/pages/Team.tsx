import { useMemo, useState } from "react";
import { Badge } from "../components/Badge";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { TeamMember } from "../types/types";
import { currency } from "../utils/helpers";

const defaultForm: Omit<TeamMember, "id"> = {
  name: "",
  email: "",
  role: "Sales",
  department: "",
  status: "active",
  assignedDeals: 0,
  tasksCompleted: 0,
  revenueGenerated: 0,
};

export default function Team() {
  const team = useCRMStore((state) => state.team);
  const addTeamMember = useCRMStore((state) => state.addTeamMember);
  const updateTeamMember = useCRMStore((state) => state.updateTeamMember);
  const deleteTeamMember = useCRMStore((state) => state.deleteTeamMember);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [form, setForm] = useState(defaultForm);

  const teamPerformance = useMemo(
    () =>
      team.map((member) => ({
        ...member,
        winRate: member.assignedDeals ? (member.tasksCompleted / member.assignedDeals) * 100 : 0,
      })),
    [team]
  );

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!/\S+@\S+\.\S+/.test(form.email)) {
      pushToast("Please provide a valid team member email.", "error");
      return;
    }
    if (editId) {
      updateTeamMember(editId, form);
      pushToast("Team member updated successfully.", "success");
    } else {
      addTeamMember(form);
      pushToast("Team member created successfully.", "success");
    }
    setForm(defaultForm);
    setEditId(null);
    setOpen(false);
  };

  return (
    <div className="stack">
      <div className="grid cols-4">
        {teamPerformance.slice(0, 4).map((member) => (
          <Card key={member.id} title={member.name} subtitle={`${member.role} • ${member.department}`}>
            <p>Assigned deals: {member.assignedDeals}</p>
            <p>Tasks completed: {member.tasksCompleted}</p>
            <p>Revenue generated: {currency(member.revenueGenerated)}</p>
            <p>Win rate: {member.winRate.toFixed(1)}%</p>
          </Card>
        ))}
      </div>

      <Card title="Team" subtitle="Create, edit, delete, role management and performance metrics" actions={<Button onClick={() => setOpen(true)}>Add Team Member</Button>}>
        <Table
          rows={teamPerformance}
          emptyMessage="No team members yet."
          columns={[
            { header: "Name", render: (row) => row.name },
            { header: "Email", render: (row) => row.email },
            { header: "Department", render: (row) => row.department },
            {
              header: "Role",
              render: (row) => (
                <Select
                  value={row.role}
                  onChange={(event) => {
                    updateTeamMember(row.id, { ...row, role: event.target.value as TeamMember["role"] });
                    pushToast("Role updated.", "success");
                  }}
                >
                  <option value="Admin">Admin</option>
                  <option value="Sales">Sales</option>
                  <option value="Finance">Finance</option>
                  <option value="Support">Support</option>
                </Select>
              ),
            },
            { header: "Assigned Deals", render: (row) => row.assignedDeals },
            { header: "Tasks Completed", render: (row) => row.tasksCompleted },
            { header: "Revenue", render: (row) => currency(row.revenueGenerated) },
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
                      if (!window.confirm("Delete this team member?")) return;
                      deleteTeamMember(row.id);
                      pushToast("Team member deleted.", "info");
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

      <Modal title={editId ? "Edit Team Member" : "Create Team Member"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Input placeholder="Name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required />
          <Input placeholder="Email" type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} required />
          <Select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value as TeamMember["role"] })}>
            <option value="Admin">Admin</option>
            <option value="Sales">Sales</option>
            <option value="Finance">Finance</option>
            <option value="Support">Support</option>
          </Select>
          <Input placeholder="Department" value={form.department} onChange={(event) => setForm({ ...form, department: event.target.value })} required />
          <Select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as TeamMember["status"] })}>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </Select>
          <Input
            type="number"
            min={0}
            placeholder="Assigned deals"
            value={form.assignedDeals}
            onChange={(event) => setForm({ ...form, assignedDeals: Number(event.target.value) })}
            required
          />
          <Input
            type="number"
            min={0}
            placeholder="Tasks completed"
            value={form.tasksCompleted}
            onChange={(event) => setForm({ ...form, tasksCompleted: Number(event.target.value) })}
            required
          />
          <Input
            type="number"
            min={0}
            placeholder="Revenue generated"
            value={form.revenueGenerated}
            onChange={(event) => setForm({ ...form, revenueGenerated: Number(event.target.value) })}
            required
          />
          <Button type="submit">{editId ? "Save Member" : "Create Member"}</Button>
        </form>
      </Modal>
    </div>
  );
}
