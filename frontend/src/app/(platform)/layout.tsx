import { Sidebar } from "@/components/platform/sidebar";
import { Topbar } from "@/components/platform/topbar";
import { ToastContainer } from "@/components/ui/toast";
import { WebSocketProvider } from "@/components/platform/websocket-provider";

export default function PlatformLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <div className="flex-1">
        <Topbar />
        <main className="p-4 lg:p-8">{children}</main>
      </div>
      <ToastContainer />
      <WebSocketProvider />
    </div>
  );
}

