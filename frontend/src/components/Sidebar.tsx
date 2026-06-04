import { NavLink } from "react-router-dom";

const links = [
  { to: "/", label: "Dashboard" },
  { to: "/documents", label: "Documents" },
  { to: "/executions", label: "Executions" },
  { to: "/workflows", label: "Workflows" },
  { to: "/analytics", label: "Analytics" },
  { to: "/vendors", label: "Vendors" },
  { to: "/approvals", label: "Approvals" },
  { to: "/reports", label: "Reports" },
];

export function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <p className="kicker">AI Operating System</p>
        <h2>InvoiceMind AI</h2>
      </div>
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
      <div className="sidebar-footer">
        <span className="badge badge-info">Neural Sync: Online</span>
      </div>
    </aside>
  );
}
