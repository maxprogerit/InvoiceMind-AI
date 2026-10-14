import { create } from "zustand";

import type { InvoiceStatus, StatusEvent } from "@/lib/api/types";

interface LiveState {
  connected: boolean;
  /** Latest pipeline status per invoice id, as pushed by the server. Used for instant UI updates. */
  latest: Record<string, { status: InvoiceStatus; at: string; failureReason?: string }>;
  setConnected: (connected: boolean) => void;
  apply: (event: StatusEvent) => void;
}

export const useLive = create<LiveState>((set) => ({
  connected: false,
  latest: {},
  setConnected: (connected) => set({ connected }),
  apply: (e) =>
    set((s) => ({ latest: { ...s.latest, [e.invoiceId]: { status: e.status, at: e.at, failureReason: e.failureReason } } })),
}));
