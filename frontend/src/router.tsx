import type { ReactElement } from "react";
import { Navigate, createBrowserRouter } from "react-router-dom";
import { Layout } from "./components/Layout";
import { useCRMStore } from "./store/crmStore";
import Analytics from "./pages/Analytics";
import Approvals from "./pages/Approvals";
import Dashboard from "./pages/Dashboard";
import Documents from "./pages/Documents";
import Executions from "./pages/Executions";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Reports from "./pages/Reports";
import Vendors from "./pages/Vendors";
import Workflows from "./pages/Workflows";

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
      { path: "documents", element: <Documents /> },
      { path: "executions", element: <Executions /> },
      { path: "workflows", element: <Workflows /> },
      { path: "analytics", element: <Analytics /> },
      { path: "vendors", element: <Vendors /> },
      { path: "approvals", element: <Approvals /> },
      { path: "reports", element: <Reports /> },
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
]);
