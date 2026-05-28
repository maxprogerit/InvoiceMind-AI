import { useState } from "react";
import { Badge } from "../components/Badge";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { TeamMember } from "../types/types";

const defaultForm: Omit<TeamMember, "id"> = {
  name: "",
  email: "",
  role: "Sales",
  status: "active",
};

export default function Team() {
  const team = useCRMStore((state) => state.team);
  const addTeamMember = useCRMStore((state) => state.addTeamMember);
  const updateTeamMember = useCRMStore((state) => state.updateTeamMember);
  const deleteTeamMember = useCRMStore((state) => state.deleteTeamMember);
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [form, setForm] = useState(defaultForm);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (editId) updateTeamMember(editId, form);
    else addTeamMember(form);
    setForm(defaultForm);
    setEditId(null);
    setOpen(false);
  };

  return (
    <Card title="Team" subtitle="Manage team members" actions={<Button onClick={() => setOpen(true)}>Add Member</Button>}>
      <Table
        rows={team}
        emptyMessage="No team members yet."
        columns={[
          { header: "Name", render: (row) => row.name },
          { header: "Email", render: (row) => row.email },
          { header: "Role", render: (row) => row.role },
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
                    setForm({ name: row.name, email: row.email, role: row.role, status: row.status });
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button variant="danger" onClick={() => deleteTeamMember(row.id)}>
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />

      <Modal title={editId ? "Edit Team Member" : "Add Team Member"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Input placeholder="Name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required />
          <Input placeholder="Email" type="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} required />
          <Select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value as TeamMember["role"] })}>
            <option value="Admin">Admin</option>
            <option value="Sales">Sales</option>
            <option value="Finance">Finance</option>
            <option value="Support">Support</option>
          </Select>
          <Select value={form.status} onChange={(event) => setForm({ ...form, status: event.target.value as TeamMember["status"] })}>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </Select>
          <Button type="submit">{editId ? "Save Member" : "Add Member"}</Button>
        </form>
      </Modal>
    </Card>
  );
}
