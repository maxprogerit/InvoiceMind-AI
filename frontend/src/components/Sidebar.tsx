import { NavLink } from "react-router-dom";

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
  return (
    <aside className="sidebar">
      <h2>InvoiceMind CRM</h2>
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
