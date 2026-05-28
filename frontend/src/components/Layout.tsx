import { Outlet } from "react-router-dom";
import { AIAssistant } from "./AIAssistant";
import { Header } from "./Header";
import { Sidebar } from "./Sidebar";
import { Toast } from "./Toast";

export function Layout() {
  return (
    <div className="layout">
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
