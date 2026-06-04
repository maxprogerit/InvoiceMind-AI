import { useLocation, useNavigate } from "react-router-dom";
import { Button } from "./Button";
import { useCRMStore } from "../store/crmStore";

const titles: Record<string, string> = {
  "/": "Dashboard",
  "/documents": "Documents",
  "/executions": "Executions",
  "/workflows": "Workflows",
  "/analytics": "Analytics",
  "/vendors": "Vendors",
  "/approvals": "Approvals",
  "/reports": "Reports",
};

const subtitles: Record<string, string> = {
  "/": "AI Document Automation Platform",
  "/documents": "Premium document intelligence layer",
  "/executions": "Live orchestration and processing telemetry",
  "/workflows": "Automation graph and neural routing",
  "/analytics": "Enterprise intelligence center",
  "/vendors": "Vendor intelligence profiles",
  "/approvals": "Human-in-the-loop review center",
  "/reports": "Automated reporting command deck",
};

export function Header() {
  const location = useLocation();
  const navigate = useNavigate();
  const logout = useCRMStore((state) => state.logout);
  const user = useCRMStore((state) => state.currentUser);

  return (
    <header className="header">
      <div>
        <h1>{titles[location.pathname] ?? "CRM"}</h1>
        <p className="muted">{subtitles[location.pathname] ?? "AI Operating System for Business Documents"}</p>
      </div>
      <div className="row">
        <span className="header-chip">Signed in as {user?.name ?? "Guest"}</span>
        <Button
          variant="ghost"
          onClick={() => {
            logout();
            navigate("/login");
          }}
        >
          Logout
        </Button>
      </div>
    </header>
  );
}
