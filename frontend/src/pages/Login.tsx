import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { useCRMStore } from "../store/crmStore";

export default function Login() {
  const login = useCRMStore((state) => state.login);
  const pushToast = useCRMStore((state) => state.pushToast);
  const navigate = useNavigate();
  const [email, setEmail] = useState("admin@invoicemind.ai");
  const [password, setPassword] = useState("admin123");

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    try {
      login({ email, password });
      pushToast("Welcome back!", "success");
      navigate("/");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to login";
      pushToast(message, "error");
    }
  };

  return (
    <div className="auth-wrap">
      <Card title="Login" subtitle="Access your CRM workspace">
        <form className="form" onSubmit={submit}>
          <Input placeholder="Email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          <Input placeholder="Password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          <Button type="submit">Login</Button>
        </form>
        <p className="muted">
          No account? <Link to="/register">Create one</Link>
        </p>
      </Card>
    </div>
  );
}
