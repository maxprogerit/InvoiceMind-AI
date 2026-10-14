import { create } from "zustand";

import type { AuthUser } from "@/lib/api/types";

type Status = "idle" | "loading" | "authenticated" | "anonymous";

interface AuthState {
  status: Status;
  /** Access token lives in memory only. The long-lived refresh token is an httpOnly cookie JS cannot read. */
  token: string | null;
  user: AuthUser | null;
  setSession: (token: string, user: AuthUser) => void;
  setStatus: (status: Status) => void;
  clear: () => void;
}

export const useAuth = create<AuthState>((set) => ({
  status: "idle",
  token: null,
  user: null,
  setSession: (token, user) => set({ token, user, status: "authenticated" }),
  setStatus: (status) => set({ status }),
  clear: () => set({ token: null, user: null, status: "anonymous" }),
}));

/** Milliseconds until the JWT expires (negative if already expired, 0 if unreadable). */
export function msUntilExpiry(token: string | null): number {
  if (!token) return 0;
  try {
    const payload = JSON.parse(atob(token.split(".")[1]!.replace(/-/g, "+").replace(/_/g, "/"))) as { exp?: number };
    return payload.exp ? payload.exp * 1000 - Date.now() : 0;
  } catch {
    return 0;
  }
}
