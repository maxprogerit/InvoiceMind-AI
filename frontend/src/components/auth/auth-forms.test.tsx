import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SSO_ERRORS, LoginForm } from "./auth-forms";

const search = vi.hoisted(() => ({ value: new URLSearchParams() }));
const config = vi.hoisted(() => ({ data: undefined as undefined | { sso: { enabled: boolean; label?: string } } }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => search.value,
}));
vi.mock("@/lib/api/hooks", () => ({ useAuthConfig: () => ({ data: config.data }) }));

function renderForm() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <LoginForm />
    </QueryClientProvider>,
  );
}

describe("LoginForm single sign-on", () => {
  beforeEach(() => {
    search.value = new URLSearchParams();
    config.data = undefined;
  });

  it("offers no SSO button unless the server enables it", () => {
    config.data = { sso: { enabled: false } };
    renderForm();
    expect(screen.queryByRole("link", { name: /continue with/i })).not.toBeInTheDocument();
  });

  it("links the button to the backend flow, keeping a same-site next page", () => {
    config.data = { sso: { enabled: true, label: "Acme SSO" } };
    search.value = new URLSearchParams("next=/approvals");
    renderForm();
    expect(screen.getByRole("link", { name: "Continue with Acme SSO" })).toHaveAttribute("href", "/api/v1/auth/oidc/login?next=%2Fapprovals");
  });

  it("never forwards an off-site next target", () => {
    config.data = { sso: { enabled: true, label: "Acme SSO" } };
    search.value = new URLSearchParams("next=https://evil.example/phish");
    renderForm();
    expect(screen.getByRole("link", { name: /continue with/i })).toHaveAttribute("href", "/api/v1/auth/oidc/login?next=%2Finvoices");
  });

  it("explains why the provider flow ended", () => {
    search.value = new URLSearchParams("sso_error=no_account");
    renderForm();
    expect(screen.getByRole("alert")).toHaveTextContent(String(SSO_ERRORS.no_account));
  });

  it("falls back to a generic message for an unknown error code", () => {
    search.value = new URLSearchParams("sso_error=something_new");
    renderForm();
    expect(screen.getByRole("alert")).toHaveTextContent("Single sign-on failed");
  });
});
