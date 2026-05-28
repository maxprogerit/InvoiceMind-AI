import { useEffect, useState } from "react";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { Select } from "../components/Select";
import { useCRMStore } from "../store/crmStore";
import type { AppSettings } from "../types/types";

export default function Settings() {
  const settings = useCRMStore((state) => state.settings);
  const updateSettings = useCRMStore((state) => state.updateSettings);
  const pushToast = useCRMStore((state) => state.pushToast);
  const [form, setForm] = useState<AppSettings>(settings);

  useEffect(() => {
    document.body.dataset.theme = form.theme;
  }, [form.theme]);

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    updateSettings(form);
    pushToast("Settings saved.", "success");
  };

  return (
    <div className="stack">
      <Card title="Profile Settings">
        <form className="form" onSubmit={submit}>
          <Input
            placeholder="Profile name"
            value={form.profileName}
            onChange={(event) => setForm({ ...form, profileName: event.target.value })}
            required
          />
          <Input
            placeholder="Company name"
            value={form.companyName}
            onChange={(event) => setForm({ ...form, companyName: event.target.value })}
            required
          />
          <Input
            placeholder="Workspace label"
            value={form.workspaceLabel}
            onChange={(event) => setForm({ ...form, workspaceLabel: event.target.value })}
            required
          />
          <Select value={form.billingPlan} onChange={(event) => setForm({ ...form, billingPlan: event.target.value as AppSettings["billingPlan"] })}>
            <option value="Starter">Starter</option>
            <option value="Pro">Pro</option>
            <option value="Enterprise">Enterprise</option>
          </Select>
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
              checked={form.notifications.deals}
              onChange={(event) => setForm({ ...form, notifications: { ...form.notifications, deals: event.target.checked } })}
            />
            Deal notifications
          </label>
          <label className="row">
            <input
              type="checkbox"
              checked={form.notifications.invoices}
              onChange={(event) => setForm({ ...form, notifications: { ...form.notifications, invoices: event.target.checked } })}
            />
            Invoice notifications
          </label>
          <label className="row">
            <input
              type="checkbox"
              checked={form.notifications.reminders}
              onChange={(event) => setForm({ ...form, notifications: { ...form.notifications, reminders: event.target.checked } })}
            />
            Reminder notifications
          </label>
          <label className="row">
            <input
              type="checkbox"
              checked={form.security.twoFactor}
              onChange={(event) => setForm({ ...form, security: { twoFactor: event.target.checked } })}
            />
            Two factor security
          </label>
          <Input
            placeholder="Slack webhook URL"
            value={form.apiIntegrations.slackWebhook}
            onChange={(event) =>
              setForm({ ...form, apiIntegrations: { ...form.apiIntegrations, slackWebhook: event.target.value } })
            }
          />
          <Button type="submit">Save Settings</Button>
        </form>
      </Card>

      <Card title="Billing & Workspace">
        <p>Current plan: <strong>{form.billingPlan}</strong></p>
        <div className="row">
          <Button variant="secondary" onClick={() => pushToast("Upgrade to Pro opened.", "success")}>
            Upgrade to Pro
          </Button>
          <Button variant="secondary" onClick={() => pushToast("Workspace data export started.", "success")}>
            Data Export
          </Button>
          <Button variant="danger" onClick={() => pushToast("Delete workspace UI opened. Confirmation required.", "error")}>
            Delete Workspace
          </Button>
        </div>
      </Card>
    </div>
  );
}
