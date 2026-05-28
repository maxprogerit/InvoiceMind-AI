import { NavLink } from "react-router-dom";
import { useCRMStore } from "../store/crmStore";

const links = [
  { to: "/", label: "Dashboard" },
  { to: "/clients", label: "Clients" },
  { to: "/deals", label: "Deals" },
  { to: "/contacts", label: "Contacts" },
  { to: "/companies", label: "Companies" },
  { to: "/calendar", label: "Calendar" },
  { to: "/documents", label: "Documents" },
  { to: "/invoices", label: "Invoices" },
  { to: "/receipts", label: "Receipts" },
  { to: "/reports", label: "Reports" },
  { to: "/analytics", label: "Analytics" },
  { to: "/team", label: "Team" },
  { to: "/settings", label: "Settings" },
];

export function Sidebar() {
  const workspace = useCRMStore((state) => state.settings.workspaceLabel);
  const companyName = useCRMStore((state) => state.settings.companyName);
  return (
    <aside className="sidebar">
      <h2>{companyName}</h2>
      <p className="muted">{workspace}</p>
      <nav>
        {links.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            className={({ isActive }) => (isActive ? "nav-link active" : "nav-link")}
            end={link.to === "/"}
          >
            {link.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
