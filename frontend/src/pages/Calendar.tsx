import { useMemo, useState } from "react";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Modal } from "../components/Modal";
import { Table } from "../components/Table";
import { useCRMStore } from "../store/crmStore";
import type { CalendarEvent } from "../types/types";
import { byQuery } from "../utils/helpers";

const defaultForm: Omit<CalendarEvent, "id"> = { title: "", date: "", type: "", notes: "" };

export default function Calendar() {
  const events = useCRMStore((state) => state.events);
  const addEvent = useCRMStore((state) => state.addEvent);
  const updateEvent = useCRMStore((state) => state.updateEvent);
  const deleteEvent = useCRMStore((state) => state.deleteEvent);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [form, setForm] = useState(defaultForm);

  const rows = useMemo(() => byQuery(events, query, (event) => `${event.title} ${event.type}`), [events, query]);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (editId) updateEvent(editId, form);
    else addEvent(form);
    setForm(defaultForm);
    setEditId(null);
    setOpen(false);
  };

  return (
    <Card title="Calendar" subtitle="Create meetings, reminders, and events" actions={<Button onClick={() => setOpen(true)}>New Event</Button>}>
      <Input placeholder="Search event..." value={query} onChange={(event) => setQuery(event.target.value)} />
      <Table
        rows={rows}
        emptyMessage="No events yet."
        columns={[
          { header: "Title", render: (row) => row.title },
          { header: "Date", render: (row) => row.date },
          { header: "Type", render: (row) => row.type },
          { header: "Notes", render: (row) => row.notes },
          {
            header: "Actions",
            render: (row) => (
              <div className="row">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setEditId(row.id);
                    setForm({ title: row.title, date: row.date, type: row.type, notes: row.notes });
                    setOpen(true);
                  }}
                >
                  Edit
                </Button>
                <Button variant="danger" onClick={() => deleteEvent(row.id)}>
                  Delete
                </Button>
              </div>
            ),
          },
        ]}
      />
      <Modal title={editId ? "Edit Event" : "Create Event"} open={open} onClose={() => setOpen(false)}>
        <form className="form" onSubmit={submit}>
          <Input placeholder="Title" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} required />
          <Input type="date" value={form.date} onChange={(event) => setForm({ ...form, date: event.target.value })} required />
          <Input placeholder="Type (meeting, call...)" value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value })} required />
          <Input placeholder="Notes" value={form.notes} onChange={(event) => setForm({ ...form, notes: event.target.value })} required />
          <Button type="submit">{editId ? "Save Event" : "Create Event"}</Button>
        </form>
      </Modal>
    </Card>
  );
}
