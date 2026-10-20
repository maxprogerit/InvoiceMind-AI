import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAuth } from "@/lib/auth/store";

import { ClientSwitcher } from "./client-switcher";

const nav = vi.hoisted(() => ({ push: vi.fn(), pathname: "/invoices" }));
const orgs = vi.hoisted(() => ({ data: undefined as undefined | { id: string; name: string; role: string; home: boolean; active: boolean }[] }));
const mutate = vi.hoisted(() => vi.fn());

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: nav.push }), usePathname: () => nav.pathname }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("@/lib/api/hooks", () => ({
  useOrganizations: () => ({ data: orgs.data }),
  useSwitchOrganization: () => ({ mutate, isPending: false }),
}));

// Radix measures its popper content; jsdom has no ResizeObserver.
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const option = (id: string, name: string, role = "ADMIN", home = false) => ({ id, name, role, home, active: true });

describe("ClientSwitcher", () => {
  beforeEach(() => {
    nav.push.mockClear();
    nav.pathname = "/invoices";
    mutate.mockReset();
    orgs.data = [option("a", "Acme GmbH", "ADMIN", true), option("b", "Bakery Müller", "USER"), option("c", "Café Nord", "APPROVER")];
    useAuth.setState({
      status: "authenticated",
      token: "t",
      user: { id: "u", email: "a@b.io", fullName: "A", role: "ADMIN", organizationId: "a", organizationName: "Acme GmbH" },
    });
  });

  it("is not shown to somebody who can reach only one company (the fallback is shown instead)", () => {
    orgs.data = [option("a", "Acme GmbH", "ADMIN", true)];
    render(<ClientSwitcher fallback={<span>Acme GmbH (plain)</span>} />);
    expect(screen.queryByRole("button", { name: /change/i })).not.toBeInTheDocument();
    expect(screen.getByText("Acme GmbH (plain)")).toBeInTheDocument();
  });

  it("shows nothing while the list is still loading", () => {
    orgs.data = undefined;
    const { container } = render(<ClientSwitcher />);
    expect(container).toBeEmptyDOMElement();
  });

  it("names the current company and lists every company with the role held there", async () => {
    render(<ClientSwitcher />);
    await userEvent.click(screen.getByRole("button", { name: /client company: acme gmbh/i }));
    const list = await screen.findByRole("listbox", { name: /client companies/i });
    expect(list.querySelectorAll("[role=option]")).toHaveLength(3);
    expect(screen.getByRole("option", { name: /acme gmbh/i })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("option", { name: /bakery müller/i })).toHaveTextContent("user");
  });

  it("filters by what is typed and says so when nothing matches", async () => {
    render(<ClientSwitcher />);
    await userEvent.click(screen.getByRole("button", { name: /change/i }));
    await userEvent.type(await screen.findByRole("combobox", { name: /search client/i }), "bak");
    expect(screen.getAllByRole("option")).toHaveLength(1);
    expect(screen.getByRole("option", { name: /bakery/i })).toBeInTheDocument();
    await userEvent.type(screen.getByRole("combobox"), "zzz");
    expect(screen.queryByRole("option")).not.toBeInTheDocument();
    expect(screen.getByText(/no client matches/i)).toBeInTheDocument();
  });

  it("asks the server to switch when another company is chosen, and does nothing for the current one", async () => {
    render(<ClientSwitcher />);
    await userEvent.click(screen.getByRole("button", { name: /change/i }));
    await userEvent.click(await screen.findByRole("option", { name: /acme gmbh/i }));
    expect(mutate).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: /change/i }));
    await userEvent.click(await screen.findByRole("option", { name: /café nord/i }));
    expect(mutate).toHaveBeenCalledWith("c", expect.any(Object));
  });

  it("works from the keyboard: arrows move, Enter chooses", async () => {
    render(<ClientSwitcher />);
    await userEvent.click(screen.getByRole("button", { name: /change/i }));
    await screen.findByRole("combobox");
    await userEvent.keyboard("{ArrowDown}{Enter}");
    expect(mutate).toHaveBeenCalledWith("b", expect.any(Object));
  });

  it("leaves an open document, which belongs to the previous company, once the switch succeeded", async () => {
    nav.pathname = "/invoices/9f3c";
    mutate.mockImplementation((_id: string, opts: { onSuccess: () => void }) => opts.onSuccess());
    const onSwitched = vi.fn();
    render(<ClientSwitcher onSwitched={onSwitched} />);
    await userEvent.click(screen.getByRole("button", { name: /change/i }));
    await userEvent.click(await screen.findByRole("option", { name: /bakery/i }));
    await waitFor(() => expect(nav.push).toHaveBeenCalledWith("/invoices"));
    expect(onSwitched).toHaveBeenCalled();
  });

  it("stays on list pages after a switch", async () => {
    mutate.mockImplementation((_id: string, opts: { onSuccess: () => void }) => opts.onSuccess());
    render(<ClientSwitcher />);
    await userEvent.click(screen.getByRole("button", { name: /change/i }));
    await userEvent.click(await screen.findByRole("option", { name: /bakery/i }));
    expect(nav.push).not.toHaveBeenCalled();
  });
});
