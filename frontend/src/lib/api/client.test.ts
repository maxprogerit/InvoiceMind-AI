import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAuth } from "@/lib/auth/store";

import { ApiError, authFetch, getValidToken, unwrap } from "./client";

const user = { id: "u1", email: "a@b.io", fullName: "A B", role: "ADMIN", organizationId: "o1", organizationName: "Org" } as const;

function jwt(expiresInSeconds: number): string {
  const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + expiresInSeconds }));
  return `h.${payload}.s`;
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

describe("authFetch", () => {
  const fetchMock = vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
    useAuth.setState({ status: "authenticated", token: jwt(600), user });
  });
  afterEach(() => vi.unstubAllGlobals());

  const authHeader = (call: unknown[]) => (call[0] instanceof Request ? call[0].headers.get("Authorization") : null);

  it("attaches the bearer token", async () => {
    fetchMock.mockResolvedValueOnce(json(200, { ok: true }));
    const token = useAuth.getState().token;
    await authFetch("http://localhost/api/v1/invoices");
    expect(authHeader(fetchMock.mock.calls[0]!)).toBe(`Bearer ${token}`);
  });

  it("refreshes once and retries when the API answers 401", async () => {
    const fresh = jwt(900);
    fetchMock
      .mockResolvedValueOnce(json(401, { code: "unauthorized" })) // original call
      .mockResolvedValueOnce(json(200, { accessToken: fresh, user })) // refresh
      .mockResolvedValueOnce(json(200, { items: [] })); // retry
    const res = await authFetch("http://localhost/api/v1/invoices");
    expect(res.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(String(fetchMock.mock.calls[1]![0])).toContain("/api/v1/auth/refresh");
    expect(authHeader(fetchMock.mock.calls[2]!)).toBe(`Bearer ${fresh}`);
  });

  it("shares ONE refresh between concurrent callers (refresh tokens rotate, a second call would look like reuse)", async () => {
    useAuth.setState({ token: jwt(5) }); // about to expire -> both callers need a fresh token
    const fresh = jwt(900);
    let refreshCalls = 0;
    fetchMock.mockImplementation(async (input) => {
      const url = String(input instanceof Request ? input.url : input);
      if (url.includes("/auth/refresh")) {
        refreshCalls++;
        await new Promise((r) => setTimeout(r, 20));
        return json(200, { accessToken: fresh, user });
      }
      return json(200, {});
    });
    await Promise.all([getValidToken(), getValidToken(), authFetch("http://localhost/api/v1/a"), authFetch("http://localhost/api/v1/b")]);
    expect(refreshCalls).toBe(1);
  });

  it("signs the user out when the refresh fails", async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, {}))
      .mockResolvedValueOnce(json(401, { code: "unauthorized" })); // refresh rejected
    const res = await authFetch("http://localhost/api/v1/invoices");
    expect(res.status).toBe(401);
    expect(useAuth.getState().status).toBe("anonymous");
    expect(useAuth.getState().token).toBeNull();
  });

  it("never loops on the auth endpoints themselves", async () => {
    fetchMock.mockResolvedValueOnce(json(401, { code: "unauthorized" }));
    const res = await authFetch("http://localhost/api/v1/auth/login", { method: "POST", body: "{}" });
    expect(res.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe("unwrap", () => {
  it("returns data on success", async () => {
    await expect(unwrap(Promise.resolve({ data: { a: 1 }, response: json(200, {}) }))).resolves.toEqual({ a: 1 });
  });

  it("throws a typed ApiError carrying the server's code, message and field errors", async () => {
    const response = json(400, { code: "validation_failed", message: "Request validation failed", fieldErrors: { password: "too short" }, requestId: "abc" });
    const err = await unwrap(Promise.resolve({ error: {}, response })).catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ status: 400, code: "validation_failed", fieldErrors: { password: "too short" }, requestId: "abc" });
  });

  it("explains an nginx 413 and survives non-JSON error bodies", async () => {
    const big = await unwrap(Promise.resolve({ response: new Response("<html>413</html>", { status: 413 }) })).catch((e: unknown) => e);
    expect((big as ApiError).message).toContain("15 MB");
    const crash = await unwrap(Promise.resolve({ response: new Response("boom", { status: 502 }) })).catch((e: unknown) => e);
    expect((crash as ApiError).message).toBe("Something went wrong on our side");
  });
});
