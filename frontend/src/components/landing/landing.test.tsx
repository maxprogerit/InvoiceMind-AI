import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import Home from "@/app/page";
import { useAuth } from "@/lib/auth/store";

import { REPO_URL } from "./landing";

const replace = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));

const setStatus = (status: "idle" | "loading" | "authenticated" | "anonymous") => useAuth.setState({ status, token: null, user: null });

describe("the public front door", () => {
  beforeEach(() => replace.mockClear());

  it("shows the landing page to somebody who is not signed in", () => {
    setStatus("anonymous");
    render(<Home />);
    expect(screen.getByRole("heading", { level: 1, name: "Every invoice runs the same gate." })).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("offers real links, not clickable boxes: sign in, register, the sample, the repository", () => {
    setStatus("anonymous");
    render(<Home />);
    const hrefOf = (name: string) => screen.getAllByRole("link", { name }).map((a) => a.getAttribute("href"));
    expect(hrefOf("Sign in")).toContain("/login");
    expect(hrefOf("Create account")).toContain("/register");
    expect(hrefOf("See a sample")).toEqual(["#sample"]);
    const repo = screen.getByRole("link", { name: "View on GitHub" });
    expect(repo).toHaveAttribute("href", REPO_URL);
    expect(repo).toHaveAttribute("rel", expect.stringContaining("noopener"));
    expect(screen.queryAllByRole("button")).toHaveLength(0);
    expect(document.getElementById("sample")).not.toBeNull();   // the target of "See a sample"
  });

  it("describes the drawing once and hides its inner text from assistive technology", () => {
    setStatus("anonymous");
    render(<Home />);
    const image = screen.getByRole("img", { name: /conveyor belt through a scan gate/i });
    expect(image.firstElementChild).toHaveAttribute("aria-hidden", "true");
  });

  it("states what was measured as a small synthetic smoke test", () => {
    setStatus("anonymous");
    render(<Home />);
    expect(screen.getByText(/18 synthetic documents/)).toHaveTextContent(/not a benchmark/);
  });

  it.each(["idle", "loading"] as const)("shows only the splash while the session is being restored (%s)", (status) => {
    setStatus(status);
    render(<Home />);
    expect(screen.getByRole("status", { name: "Loading" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
  });

  it("sends a signed-in person to their invoices without showing the landing page", () => {
    setStatus("authenticated");
    render(<Home />);
    expect(replace).toHaveBeenCalledWith("/invoices");
    expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
  });
});
