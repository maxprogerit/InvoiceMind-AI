"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "motion/react";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/input";
import { useAuthConfig } from "@/lib/api/hooks";
import { ApiError, api, unwrap } from "@/lib/api/client";
import type { AuthUser } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/store";

type AuthBody = { accessToken: string; user: AuthUser };

const passwordRule = z
  .string()
  .min(10, "At least 10 characters")
  .max(128)
  .regex(/[A-Za-z]/, "Include a letter")
  .regex(/\d/, "Include a digit");

const loginSchema = z.object({ email: z.string().email("Enter a valid e-mail"), password: z.string().min(1, "Enter your password") });
const registerSchema = z.object({
  organizationName: z.string().trim().min(1, "Required").max(200),
  fullName: z.string().trim().min(1, "Required").max(200),
  email: z.string().email("Enter a valid e-mail"),
  password: passwordRule,
});

function Shell({ title, subtitle, children, footer }: { title: string; subtitle: string; children: React.ReactNode; footer: React.ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.2 }}
      className="w-full max-w-[460px] rounded-2xl border border-line bg-surface/90 p-8 shadow-[0_30px_80px_-30px_rgb(0_0_0/0.8)] backdrop-blur sm:p-10"
    >
      <h2 className="text-3xl font-bold tracking-tight">{title}</h2>
      <p className="mt-2 text-muted">{subtitle}</p>
      <div className="mt-8">{children}</div>
      <p className="mt-8 text-center font-mono text-[13px] text-dim">{footer}</p>
    </motion.div>
  );
}

function FieldError({ message }: { message?: string }) {
  return message ? (
    <p role="alert" className="mt-1.5 text-xs text-bad">
      {message}
    </p>
  ) : null;
}

/** Why the identity provider flow ended on the login page (?sso_error=code, set by the backend). */
export const SSO_ERRORS: Record<string, string> = {
  not_configured: "Single sign-on is not set up correctly. Ask an administrator.",
  state_mismatch: "The sign-in attempt expired or could not be verified. Please try again.",
  idp_unreachable: "The identity provider could not be reached. Try again in a moment.",
  idp_error: "The identity provider refused the sign-in.",
  token_exchange_failed: "The identity provider rejected the sign-in. Please try again.",
  invalid_token: "The identity provider's answer could not be verified.",
  no_email: "The identity provider did not share your e-mail address.",
  email_unverified: "Your e-mail address is not verified at the identity provider.",
  no_account: "There is no account for your e-mail address yet. Ask an administrator to add you.",
  identity_mismatch: "This account is linked to a different identity at the identity provider.",
  domain_not_allowed: "Accounts for your e-mail domain are not created automatically. Ask an administrator.",
  disabled: "This account is disabled.",
};

function describe(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Invalid e-mail or password.";
    if (error.status === 429) return "Too many attempts. Please wait a minute and try again.";
    if (error.status === 409) return "An account with this e-mail already exists.";
    return error.message;
  }
  return "Could not reach the server. Check your connection and try again.";
}

export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const ssoError = params.get("sso_error");
  const [error, setError] = useState<string | null>(ssoError ? (SSO_ERRORS[ssoError] ?? "Single sign-on failed. Please try again.") : null);
  const sso = useAuthConfig().data?.sso;
  const nextParam = params.get("next");
  const ssoHref = `/api/v1/auth/oidc/login?next=${encodeURIComponent(nextParam && nextParam.startsWith("/") && !nextParam.startsWith("//") ? nextParam : "/invoices")}`;
  const form = useForm<z.infer<typeof loginSchema>>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const data = (await unwrap(api.POST("/api/v1/auth/login", { body: values }))) as AuthBody;
      useAuth.getState().setSession(data.accessToken, data.user);
      const next = params.get("next");
      router.replace(next && next.startsWith("/") && !next.startsWith("//") ? next : "/invoices");
    } catch (e) {
      setError(describe(e));
    }
  });

  return (
    <Shell
      title="Sign in"
      subtitle="Welcome back. Pick up where you left off."
      footer={
        <>
          No account yet?{" "}
          <Link href="/register" className="text-accent hover:underline">
            Create one
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-5" noValidate>
        <div>
          <Label htmlFor="email">E-mail</Label>
          <Input id="email" type="email" autoComplete="email" placeholder="you@company.com" aria-invalid={!!form.formState.errors.email} {...form.register("email")} />
          <FieldError message={form.formState.errors.email?.message} />
        </div>
        <div>
          <Label htmlFor="password">Password</Label>
          <Input id="password" type="password" autoComplete="current-password" aria-invalid={!!form.formState.errors.password} {...form.register("password")} />
          <FieldError message={form.formState.errors.password?.message} />
        </div>
        {error && (
          <p role="alert" className="rounded-control border border-bad/30 bg-bad/10 px-3 py-2 text-sm text-bad">
            {error}
          </p>
        )}
        <Button type="submit" size="lg" className="w-full" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting && <Loader2 className="animate-spin" />}
          Sign in
        </Button>
      </form>
      {sso?.enabled && (
        <div className="mt-6">
          <div className="mb-4 flex items-center gap-3 font-mono text-[11px] uppercase tracking-wider text-dim">
            <span className="h-px flex-1 bg-line" /> or <span className="h-px flex-1 bg-line" />
          </div>
          {/* a plain link: the browser must follow the redirects to the identity provider and back */}
          <Button asChild variant="outline" size="lg" className="w-full">
            <a href={ssoHref}>Continue with {sso.label ?? "single sign-on"}</a>
          </Button>
        </div>
      )}
    </Shell>
  );
}

export function RegisterForm() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<z.infer<typeof registerSchema>>({
    resolver: zodResolver(registerSchema),
    defaultValues: { organizationName: "", fullName: "", email: "", password: "" },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const data = (await unwrap(api.POST("/api/v1/auth/register", { body: values }))) as AuthBody;
      useAuth.getState().setSession(data.accessToken, data.user);
      router.replace("/invoices");
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors) {
        for (const [field, message] of Object.entries(e.fieldErrors)) {
          if (field in values) form.setError(field as keyof typeof values, { message });
        }
      }
      setError(describe(e));
    }
  });

  const e = form.formState.errors;
  return (
    <Shell
      title="Create your workspace"
      subtitle="You become the admin and can invite your team afterwards."
      footer={
        <>
          Already registered?{" "}
          <Link href="/login" className="text-accent hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <div>
          <Label htmlFor="org">Company</Label>
          <Input id="org" autoComplete="organization" placeholder="Acme GmbH" aria-invalid={!!e.organizationName} {...form.register("organizationName")} />
          <FieldError message={e.organizationName?.message} />
        </div>
        <div>
          <Label htmlFor="name">Your name</Label>
          <Input id="name" autoComplete="name" placeholder="Jane Doe" aria-invalid={!!e.fullName} {...form.register("fullName")} />
          <FieldError message={e.fullName?.message} />
        </div>
        <div>
          <Label htmlFor="email">E-mail</Label>
          <Input id="email" type="email" autoComplete="email" placeholder="you@company.com" aria-invalid={!!e.email} {...form.register("email")} />
          <FieldError message={e.email?.message} />
        </div>
        <div>
          <Label htmlFor="password">Password</Label>
          <Input id="password" type="password" autoComplete="new-password" placeholder="10+ characters, a letter and a digit" aria-invalid={!!e.password} {...form.register("password")} />
          <FieldError message={e.password?.message} />
        </div>
        {error && (
          <p role="alert" className="rounded-control border border-bad/30 bg-bad/10 px-3 py-2 text-sm text-bad">
            {error}
          </p>
        )}
        <Button type="submit" size="lg" className="w-full" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting && <Loader2 className="animate-spin" />}
          Create account
        </Button>
      </form>
    </Shell>
  );
}
