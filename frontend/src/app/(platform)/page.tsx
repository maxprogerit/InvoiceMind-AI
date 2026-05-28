import { KpiCards } from "@/components/dashboard/kpi-cards";
import { LiveExecutions } from "@/components/dashboard/live-executions";
import { RecentInvoices } from "@/components/dashboard/recent-invoices";
import { SpendingChart } from "@/components/dashboard/spending-chart";
import { GlobalWidgets } from "@/components/dashboard/global-widgets";
import { DashboardLiveStatus } from "@/features/dashboard/dashboard-live-status";

export default function DashboardPage() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-semibold text-foreground">Enterprise dashboard</h2>
        <p className="mt-1 text-slate-400">AI automation performance, workflow activity, and finance operations in real time.</p>
      </div>
      <KpiCards />
      <GlobalWidgets />
      <DashboardLiveStatus />
      <div className="grid gap-6 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <SpendingChart />
        </div>
        <LiveExecutions />
      </div>
      <RecentInvoices />
    </div>
  );
}
