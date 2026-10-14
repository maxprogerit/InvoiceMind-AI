import { beforeEach, describe, expect, it } from "vitest";

import { msUntilExpiry, useAuth } from "./store";

const user = { id: "u", email: "a@b.io", fullName: "A", role: "USER", organizationId: "o", organizationName: "O" } as const;

describe("auth store", () => {
  beforeEach(() => useAuth.setState({ status: "idle", token: null, user: null, signedOut: false }));

  it("remembers an explicit sign-out so the login page does not offer to go back", () => {
    useAuth.getState().setSession("t", user);
    useAuth.getState().clear(true);
    expect(useAuth.getState()).toMatchObject({ status: "anonymous", token: null, user: null, signedOut: true });
  });

  it("treats an expired session differently from a sign-out, and resets the flag on login", () => {
    useAuth.getState().clear();
    expect(useAuth.getState().signedOut).toBe(false);
    useAuth.getState().clear(true);
    useAuth.getState().setSession("t", user);
    expect(useAuth.getState().signedOut).toBe(false);
  });

  it("reads token expiry without verifying it", () => {
    const soon = `h.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 60 }))}.s`;
    expect(msUntilExpiry(soon)).toBeGreaterThan(55_000);
    expect(msUntilExpiry(`h.${btoa(JSON.stringify({ exp: 1 }))}.s`)).toBeLessThan(0);
    expect(msUntilExpiry("garbage")).toBe(0);
    expect(msUntilExpiry(null)).toBe(0);
  });
});
