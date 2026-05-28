import type { ReactElement } from "react";
import { Navigate, createBrowserRouter } from "react-router-dom";
import { Layout } from "./components/Layout";
import { useCRMStore } from "./store/crmStore";
import Analytics from "./pages/Analytics";
import Calendar from "./pages/Calendar";
import Clients from "./pages/Clients";
import Companies from "./pages/Companies";
import Contacts from "./pages/Contacts";
import Dashboard from "./pages/Dashboard";
import Deals from "./pages/Deals";
import Documents from "./pages/Documents";
import Invoices from "./pages/Invoices";
import Login from "./pages/Login";
import Receipts from "./pages/Receipts";
import Register from "./pages/Register";
import Reports from "./pages/Reports";
import Settings from "./pages/Settings";
import Team from "./pages/Team";

function ProtectedRoute() {
  const isAuthenticated = useCRMStore((state) => state.isAuthenticated);
  return isAuthenticated ? <Layout /> : <Navigate to="/login" replace />;
}

function PublicOnly({ children }: { children: ReactElement }) {
  const isAuthenticated = useCRMStore((state) => state.isAuthenticated);
  return isAuthenticated ? <Navigate to="/" replace /> : children;
}

export const router = createBrowserRouter([
  {
    path: "/login",
    element: (
      <PublicOnly>
        <Login />
      </PublicOnly>
    ),
  },
  {
    path: "/register",
    element: (
      <PublicOnly>
        <Register />
      </PublicOnly>
    ),
  },
  {
    path: "/",
    element: <ProtectedRoute />,
    children: [
      { index: true, element: <Dashboard /> },
      { path: "clients", element: <Clients /> },
      { path: "deals", element: <Deals /> },
      { path: "contacts", element: <Contacts /> },
      { path: "companies", element: <Companies /> },
      { path: "calendar", element: <Calendar /> },
      { path: "documents", element: <Documents /> },
      { path: "invoices", element: <Invoices /> },
      { path: "receipts", element: <Receipts /> },
      { path: "reports", element: <Reports /> },
      { path: "analytics", element: <Analytics /> },
      { path: "team", element: <Team /> },
      { path: "settings", element: <Settings /> },
    ],
  },
]);
