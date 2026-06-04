import { Outlet } from "react-router-dom";
import { AIAssistant } from "./AIAssistant";
import { Header } from "./Header";
import { Sidebar } from "./Sidebar";
import { Toast } from "./Toast";

export function Layout() {
  return (
    <div className="layout shell">
      <div className="ambient-layer ambient-layer-a" />
      <div className="ambient-layer ambient-layer-b" />
      <Sidebar />
      <div className="content">
        <Header />
        <main className="main">
          <Outlet />
        </main>
      </div>
      <AIAssistant />
      <Toast />
    </div>
  );
}
