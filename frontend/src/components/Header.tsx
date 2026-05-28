import { useLocation, useNavigate } from "react-router-dom";
import { Button } from "./Button";
import { useCRMStore } from "../store/crmStore";

const titles: Record<string, string> = {
  "/": "Dashboard",
  "/clients": "Clients",
  "/deals": "Deals",
  "/contacts": "Contacts",
  "/companies": "Companies",
  "/calendar": "Calendar",
  "/documents": "Documents",
  "/invoices": "Invoices",
  "/receipts": "Receipts",
  "/reports": "Reports",
  "/analytics": "Analytics",
  "/team": "Team",
  "/settings": "Settings",
};

export function Header() {
  const location = useLocation();
  const navigate = useNavigate();
  const logout = useCRMStore((state) => state.logout);
  const pushToast = useCRMStore((state) => state.pushToast);
  const user = useCRMStore((state) => state.currentUser);
  const settings = useCRMStore((state) => state.settings);
  const unreadNotifications = useCRMStore((state) => state.notifications.filter((item) => !item.read).length);

  return (
    <header className="header">
      <div>
        <h1>{titles[location.pathname] ?? "CRM"}</h1>
        <p className="muted">{settings.workspaceLabel}</p>
      </div>
      <div className="row">
        <span className="muted">Notifications: {unreadNotifications}</span>
        <span className="muted">Signed in as {settings.profileName || user?.name || "Guest"}</span>
        <Button
          variant="secondary"
          onClick={() => {
            logout();
            pushToast("Logged out successfully.", "success");
            navigate("/login");
          }}
        >
          Logout
        </Button>
      </div>
    </header>
  );
}
