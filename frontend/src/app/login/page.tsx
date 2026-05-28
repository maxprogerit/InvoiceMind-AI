"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const router = useRouter();

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    try {
      const response = await fetch(`${API_BASE}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password })
      });
      if (!response.ok) throw new Error("Invalid credentials");
      const data = await response.json();
      localStorage.setItem("invoicemind-token", data.token);
      router.push("/");
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-6">
      <form onSubmit={onSubmit} className="w-full max-w-md rounded-2xl border border-border/60 bg-card/80 p-6 shadow-glow">
        <p className="text-xs uppercase tracking-[0.2em] text-cyan-300/70">InvoiceMind AI</p>
        <h1 className="mt-2 text-2xl font-semibold">Secure login</h1>
        <div className="mt-6 space-y-3">
          <input value={email} onChange={(e) => setEmail(e.target.value)} className="h-11 w-full rounded-xl border border-border/60 bg-black/20 px-3 outline-none" placeholder="Email" />
          <input value={password} onChange={(e) => setPassword(e.target.value)} className="h-11 w-full rounded-xl border border-border/60 bg-black/20 px-3 outline-none" type="password" placeholder="Password" />
        </div>
        {error ? <p className="mt-3 text-sm text-rose-400">{error}</p> : null}
        <Button className="mt-5 w-full">Sign in</Button>
      </form>
    </div>
  );
}
