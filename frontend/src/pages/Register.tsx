import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { Input } from "../components/Input";
import { useCRMStore } from "../store/crmStore";

export default function Register() {
  const register = useCRMStore((state) => state.register);
  const pushToast = useCRMStore((state) => state.pushToast);
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (password !== confirmPassword) {
      pushToast("Password confirmation does not match.", "error");
      return;
    }
    try {
      register({ name, email, password });
      pushToast("Account created with empty workspace.", "success");
      navigate("/");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Registration failed";
      pushToast(message, "error");
    }
  };

  return (
    <div className="auth-wrap">
      <Card title="Register" subtitle="Create a new CRM account">
        <form className="form" onSubmit={submit}>
          <Input placeholder="Full name" value={name} onChange={(event) => setName(event.target.value)} required />
          <Input placeholder="Email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          <Input placeholder="Password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          <Input
            placeholder="Confirm password"
            type="password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            required
          />
          <Button type="submit">Register</Button>
        </form>
        <p className="muted">
          Already have an account? <Link to="/login">Login</Link>
        </p>
      </Card>
    </div>
  );
}
