import { Card } from "@/components/ui/card";

export function GlobalWidgets() {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      <Card className="p-4">
        <p className="text-xs text-cyan-300/80">AI Processing Widget</p>
        <p className="mt-2 text-sm">18 documents currently in OCR + validation queue.</p>
      </Card>
      <Card className="p-4">
        <p className="text-xs text-cyan-300/80">Notification Center</p>
        <p className="mt-2 text-sm">4 approval requests and 2 anomaly alerts need review.</p>
      </Card>
      <Card className="p-4">
        <p className="text-xs text-cyan-300/80">Smart AI Assistant</p>
        <p className="mt-2 text-sm">Recommendation: Auto-route telecom invoices to low-risk path.</p>
      </Card>
      <Card className="p-4">
        <p className="text-xs text-cyan-300/80">User Profile</p>
        <p className="mt-2 text-sm">Maxim Admin • Enterprise Org • Role: Admin</p>
      </Card>
    </div>
  );
}
