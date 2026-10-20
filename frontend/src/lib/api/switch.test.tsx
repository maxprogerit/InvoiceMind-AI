import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAuth } from "@/lib/auth/store";
import { useLive } from "@/lib/ws/live-store";

import { useSwitchOrganization } from "./hooks";

const post = vi.hoisted(() => vi.fn());
vi.mock("./client", () => ({
  api: { POST: post, GET: vi.fn(), PUT: vi.fn(), DELETE: vi.fn() },
  authFetch: vi.fn(),
  fetchBlob: vi.fn(),
  toApiError: vi.fn(),
  unwrap: async (p: Promise<{ data: unknown }>) => (await p).data,
}));

const userIn = (org: string, name: string, role: "ADMIN" | "USER") =>
  ({ id: "u", email: "a@b.io", fullName: "A", role, organizationId: org, organizationName: name, firmRole: "ACCOUNTANT" }) as const;

describe("switching client company", () => {
  beforeEach(() => {
    post.mockReset();
    useAuth.setState({ status: "authenticated", token: "token-for-a", user: userIn("a", "Acme", "ADMIN"), signedOut: false });
    useLive.setState({ connected: true, latest: { inv1: { status: "VALIDATED", at: "2026-01-01T00:00:00Z" } } });
  });

  it("stores the new token, forgets everything cached for the previous company and forgets its live events", async () => {
    post.mockResolvedValue({ data: { accessToken: "token-for-b", expiresIn: 900, user: userIn("b", "Bakery", "USER") } });
    const qc = new QueryClient();
    qc.setQueryData(["invoices", {}], { items: ["invoice of Acme"] });
    const { result } = renderHook(() => useSwitchOrganization(), {
      wrapper: ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>,
    });

    await act(async () => {
      result.current.mutate("b");
    });
    await waitFor(() => expect(useAuth.getState().token).toBe("token-for-b"));

    expect(post).toHaveBeenCalledWith("/api/v1/auth/switch-organization", { body: { organizationId: "b" } });
    expect(useAuth.getState().user).toMatchObject({ organizationId: "b", role: "USER" });   // the role in the new company
    expect(qc.getQueryData(["invoices", {}])).toBeUndefined();
    expect(useLive.getState().latest).toEqual({});
  });

  it("keeps the old session when the server refuses", async () => {
    post.mockRejectedValue(new Error("403"));
    const qc = new QueryClient();
    qc.setQueryData(["invoices", {}], { items: ["invoice of Acme"] });
    const { result } = renderHook(() => useSwitchOrganization(), {
      wrapper: ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>,
    });

    await act(async () => {
      result.current.mutate("c");
    });
    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(useAuth.getState().token).toBe("token-for-a");
    expect(useAuth.getState().user?.organizationId).toBe("a");
    expect(qc.getQueryData(["invoices", {}])).toBeDefined();
  });
});
