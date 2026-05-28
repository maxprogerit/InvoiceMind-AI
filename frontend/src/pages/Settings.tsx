import { useState } from "react";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Select } from "../components/Select";
import { useCRMStore } from "../store/crmStore";

export default function Settings() {
  const settings = useCRMStore((state) => state.settings);
  const updateSettings = useCRMStore((state) => state.updateSettings);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [form, setForm] = useState(settings);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    updateSettings(form);
    pushToast("Settings saved.", "success");
  };

  return (
    <Card title="Settings" subtitle="Working global settings form">
      <form className="form" onSubmit={submit}>
        <Input
          placeholder="Company name"
          value={form.companyName}
          onChange={(event) => setForm({ ...form, companyName: event.target.value })}
          required
        />
        <Select value={form.currency} onChange={(event) => setForm({ ...form, currency: event.target.value })}>
          <option value="USD">USD</option>
          <option value="EUR">EUR</option>
          <option value="GBP">GBP</option>
        </Select>
        <Select value={form.timezone} onChange={(event) => setForm({ ...form, timezone: event.target.value })}>
          <option value="UTC">UTC</option>
          <option value="Europe/Berlin">Europe/Berlin</option>
          <option value="America/New_York">America/New_York</option>
        </Select>
        <Select value={form.theme} onChange={(event) => setForm({ ...form, theme: event.target.value as "light" | "dark" })}>
          <option value="light">Light</option>
          <option value="dark">Dark</option>
        </Select>
        <label className="row">
          <input
            type="checkbox"
            checked={form.notifications}
            onChange={(event) => setForm({ ...form, notifications: event.target.checked })}
          />
          Enable notifications
        </label>
        <Button type="submit">Save Settings</Button>
      </form>
    </Card>
  );
}
