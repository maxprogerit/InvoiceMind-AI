import { useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Badge } from "../components/Badge";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import { currency } from "../utils/helpers";

export default function Dashboard() {
  const clients = useCRMStore((state) => state.clients);
  const deals = useCRMStore((state) => state.deals);
  const invoices = useCRMStore((state) => state.invoices);
  const receipts = useCRMStore((state) => state.receipts);
  const events = useCRMStore((state) => state.events);
  const tasks = useCRMStore((state) => state.tasks);
  const team = useCRMStore((state) => state.team);
  const activity = useCRMStore((state) => state.activity);
  const addTask = useCRMStore((state) => state.addTask);
  const updateTask = useCRMStore((state) => state.updateTask);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [openTaskModal, setOpenTaskModal] = useState(false);
  const [taskTitle, setTaskTitle] = useState("");
  const [taskDueDate, setTaskDueDate] = useState("");
  const [assignedTeamMemberId, setAssignedTeamMemberId] = useState("");
  const [linkedClientId, setLinkedClientId] = useState("");

  const totalRevenue = invoices.filter((invoice) => invoice.status === "Paid").reduce((sum, invoice) => sum + invoice.total, 0);
  const totalExpenses = receipts.reduce((sum, receipt) => sum + receipt.amount, 0);
  const activeDeals = deals.filter((deal) => deal.stage !== "Won" && deal.stage !== "Lost");
  const wonDeals = deals.filter((deal) => deal.stage === "Won").length;
  const lostDeals = deals.filter((deal) => deal.stage === "Lost").length;
  const winRate = deals.length ? (wonDeals / (wonDeals + lostDeals || 1)) * 100 : 0;
  const forecastRevenue = deals.reduce((sum, deal) => sum + (deal.value * deal.probability) / 100, 0);
  const overdueInvoices = invoices.filter((invoice) => invoice.status === "Overdue").length;
  const topClients = [...clients].sort((a, b) => b.estimatedValue - a.estimatedValue).slice(0, 5);
  const upcomingEvents = [...events].filter((event) => event.date >= new Date().toISOString().slice(0, 10)).slice(0, 5);
  const pipelineData = ["New", "Qualified", "Proposal", "Negotiation", "Won", "Lost"].map((stage) => ({
    label: stage,
    value: deals.filter((deal) => deal.stage === stage).reduce((sum, deal) => sum + deal.value, 0),
  }));
  const teamPerformance = [...team]
    .map((member) => ({
      ...member,
      winRate: member.assignedDeals ? (member.tasksCompleted / Math.max(member.assignedDeals, 1)) * 100 : 0,
    }))
    .slice(0, 4);

  const agenda = useMemo(
    () => events.filter((event) => event.date === new Date().toISOString().slice(0, 10)),
    [events]
  );

  return (
    <div className="stack">
      <Card
        title="Quick Actions"
        actions={
          <div className="row">
            <Button onClick={() => setOpenTaskModal(true)}>Add Task</Button>
            <Button variant="secondary" onClick={() => pushToast("Report exported successfully.", "success")}>
              Export Report
            </Button>
          </div>
        }
      >
        <p className="muted">All dashboard metrics update from the current CRM state.</p>
      </Card>

      <div className="grid cols-4">
        <Card title="Total Revenue">
          <h2>{currency(totalRevenue)}</h2>
        </Card>
        <Card title="Total Expenses">
          <h2>{currency(totalExpenses)}</h2>
        </Card>
        <Card title="Profit">
          <h2>{currency(totalRevenue - totalExpenses)}</h2>
        </Card>
        <Card title="Forecast Revenue">
          <h2>{currency(forecastRevenue)}</h2>
        </Card>
      </div>

      <div className="grid cols-4">
        <Card title="Active Clients">
          <h2>{clients.filter((client) => client.status === "active").length}</h2>
        </Card>
        <Card title="Active Deals">
          <h2>{activeDeals.length}</h2>
        </Card>
        <Card title="Won Deals / Win Rate">
          <h2>
            {wonDeals} / {winRate.toFixed(1)}%
          </h2>
        </Card>
        <Card title="Overdue Invoices">
          <h2>{overdueInvoices}</h2>
        </Card>
      </div>

      <div className="grid cols-2">
        <Card title="Sales Pipeline Value" subtitle={`Pipeline total: ${currency(pipelineData.reduce((sum, row) => sum + row.value, 0))}`}>
          <div style={{ width: "100%", height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={pipelineData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="label" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="value" fill="#2563eb" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title="Today Agenda">
          <Table
            rows={agenda}
            emptyMessage="No meetings scheduled for today."
            columns={[
              { header: "Time", render: (row) => row.time },
              { header: "Title", render: (row) => row.title },
              { header: "Type", render: (row) => row.type },
            ]}
          />
        </Card>
      </div>

      <div className="grid cols-2">
        <Card title="Top Clients">
          <Table
            rows={topClients}
            emptyMessage="No clients yet."
            columns={[
              { header: "Client", render: (row) => row.name },
              { header: "Industry", render: (row) => row.industry },
              { header: "Estimated Value", render: (row) => currency(row.estimatedValue) },
              { header: "Status", render: (row) => <Badge tone={row.status === "active" ? "success" : "warning"}>{row.status}</Badge> },
            ]}
          />
        </Card>
        <Card title="Upcoming Events">
          <Table
            rows={upcomingEvents}
            emptyMessage="No upcoming events."
            columns={[
              { header: "Date", render: (row) => row.date },
              { header: "Time", render: (row) => row.time },
              { header: "Title", render: (row) => row.title },
            ]}
          />
        </Card>
      </div>

      <div className="grid cols-2">
        <Card title="Team Performance">
          <Table
            rows={teamPerformance}
            emptyMessage="No team members yet."
            columns={[
              { header: "Member", render: (row) => row.name },
              { header: "Revenue", render: (row) => currency(row.revenueGenerated) },
              { header: "Tasks Completed", render: (row) => row.tasksCompleted },
              { header: "Assigned Deals", render: (row) => row.assignedDeals },
            ]}
          />
        </Card>
        <Card title="Recent Activity">
          <Table
            rows={activity.slice(0, 8)}
            emptyMessage="No activity yet."
            columns={[
              { header: "When", render: (row) => new Date(row.createdAt).toLocaleString() },
              { header: "Update", render: (row) => row.text },
            ]}
          />
        </Card>
      </div>

      <Card title="Task Tracker">
        <Table
          rows={tasks}
          emptyMessage="No tasks yet. Add one from Quick Actions."
          columns={[
            { header: "Task", render: (row) => row.title },
            { header: "Due", render: (row) => row.dueDate },
            {
              header: "Status",
              render: (row) => (
                <Select
                  value={row.status}
                  onChange={(event) => {
                    updateTask(row.id, { ...row, status: event.target.value as typeof row.status });
                    pushToast("Task updated.", "success");
                  }}
                >
                  <option value="todo">To Do</option>
                  <option value="in_progress">In Progress</option>
                  <option value="done">Done</option>
                </Select>
              ),
            },
          ]}
        />
      </Card>

      <Modal title="Add Task" open={openTaskModal} onClose={() => setOpenTaskModal(false)}>
        <form
          className="form"
          onSubmit={(event) => {
            event.preventDefault();
            if (!taskTitle.trim() || !taskDueDate) {
              pushToast("Task title and due date are required.", "error");
              return;
            }
            addTask({
              title: taskTitle.trim(),
              dueDate: taskDueDate,
              status: "todo",
              assignedTeamMemberId,
              linkedClientId,
            });
            pushToast("Task created successfully.", "success");
            setTaskTitle("");
            setTaskDueDate("");
            setAssignedTeamMemberId("");
            setLinkedClientId("");
            setOpenTaskModal(false);
          }}
        >
          <Input value={taskTitle} onChange={(event) => setTaskTitle(event.target.value)} placeholder="Task title" required />
          <Input type="date" value={taskDueDate} onChange={(event) => setTaskDueDate(event.target.value)} required />
          <Select value={assignedTeamMemberId} onChange={(event) => setAssignedTeamMemberId(event.target.value)}>
            <option value="">Assign team member</option>
            {team.map((member) => (
              <option key={member.id} value={member.id}>
                {member.name}
              </option>
            ))}
          </Select>
          <Select value={linkedClientId} onChange={(event) => setLinkedClientId(event.target.value)}>
            <option value="">Link client</option>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
          </Select>
          <Button type="submit">Create Task</Button>
        </form>
      </Modal>
    </div>
  );
}
