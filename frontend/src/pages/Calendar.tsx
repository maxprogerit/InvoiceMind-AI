import { useMemo, useState } from "react";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Select } from "../components/Select";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { CalendarEvent } from "../types/types";
import { byQuery } from "../utils/helpers";

const defaultForm: Omit<CalendarEvent, "id"> = {
  title: "",
  type: "Meeting",
  date: "",
  time: "",
  linkedClientId: "",
  linkedDealId: "",
  description: "",
};

export default function Calendar() {
  const events = useCRMStore((state) => state.events);
  const clients = useCRMStore((state) => state.clients);
  const deals = useCRMStore((state) => state.deals);
  const addEvent = useCRMStore((state) => state.addEvent);
  const updateEvent = useCRMStore((state) => state.updateEvent);
  const deleteEvent = useCRMStore((state) => state.deleteEvent);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [query, setQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState("all");
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [form, setForm] = useState(defaultForm);

  const rows = useMemo(
    () =>
      byQuery(events, query, (event) => `${event.title} ${event.type} ${event.description}`)
        .filter((event) => (typeFilter === "all" ? true : event.type === typeFilter))
        .sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`)),
    [events, query, typeFilter]
  );

  const today = new Date().toISOString().slice(0, 10);
  const todayAgenda = rows.filter((event) => event.date === today);
  const upcomingMeetings = rows.filter((event) => event.date >= today).slice(0, 5);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (editId) {
      updateEvent(editId, form);
      pushToast("Event updated successfully.", "success");
    } else {
      addEvent(form);
      pushToast("Event created successfully.", "success");
    }
    setForm(defaultForm);
    setEditId(null);
    setOpen(false);
  };

  return (
    <div className="stack">
      <div className="grid cols-2">
        <Card title="Today Agenda">
          <Table
            rows={todayAgenda}
            emptyMessage="No events for today."
            columns={[
              { header: "Time", render: (row) => row.time },
              { header: "Title", render: (row) => row.title },
              { header: "Type", render: (row) => row.type },
            ]}
          />
        </Card>
        <Card title="Upcoming Meetings">
          <Table
            rows={upcomingMeetings}
            emptyMessage="No upcoming events."
            columns={[
              { header: "Date", render: (row) => row.date },
              { header: "Time", render: (row) => row.time },
              { header: "Event", render: (row) => row.title },
            ]}
          />
        </Card>
      </div>

      <Card title="Calendar" subtitle="Create, edit, delete and filter events" actions={<Button onClick={() => setOpen(true)}>Add Event</Button>}>
        <div className="grid cols-4">
          <Input placeholder="Search event..." value={query} onChange={(event) => setQuery(event.target.value)} />
          <Select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
            <option value="all">All categories</option>
            <option value="Meeting">Meeting</option>
            <option value="Call">Call</option>
            <option value="Reminder">Reminder</option>
            <option value="Task">Task</option>
          </Select>
        </div>
        <Table
          rows={rows}
          emptyMessage="No events yet."
          columns={[
            { header: "Title", render: (row) => row.title },
            { header: "Type", render: (row) => row.type },
            { header: "Date", render: (row) => row.date },
            { header: "Time", render: (row) => row.time },
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
                  <Button
                    variant="danger"
                    onClick={() => {
                      if (!window.confirm("Delete this event?")) return;
                      deleteEvent(row.id);
                      pushToast("Event deleted.", "info");
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

      <Modal title={editId ? "Edit Event" : "Create Event"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Input placeholder="Title" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} required />
          <Select value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value })}>
            <option value="Meeting">Meeting</option>
            <option value="Call">Call</option>
            <option value="Reminder">Reminder</option>
            <option value="Task">Task</option>
          </Select>
          <Input type="date" value={form.date} onChange={(event) => setForm({ ...form, date: event.target.value })} required />
          <Input type="time" value={form.time} onChange={(event) => setForm({ ...form, time: event.target.value })} required />
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
          <Input placeholder="Description" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} required />
          <Button type="submit">{editId ? "Save Event" : "Create Event"}</Button>
        </form>
      </Modal>
    </div>
  );
}
