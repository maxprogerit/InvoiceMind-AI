import { create } from "zustand";

export interface ProcessingEvent {
  documentId?: number;
  type?: string;
  status?: string;
  progress?: number;
  traceId?: string;
  payload?: Record<string, unknown>;
  timestamp: number;
}

interface RealtimeState {
  events: ProcessingEvent[];
  addEvent: (event: Omit<ProcessingEvent, "timestamp">) => void;
  clearEvents: () => void;
  getDocumentEvents: (documentId: number) => ProcessingEvent[];
}

export const useRealtimeStore = create<RealtimeState>((set, get) => ({
  events: [],
  addEvent: (event) =>
    set((s) => ({
      events: [{ ...event, timestamp: Date.now() }, ...s.events].slice(0, 100),
    })),
  clearEvents: () => set({ events: [] }),
  getDocumentEvents: (documentId) =>
    get().events.filter((e) => e.documentId === documentId),
}));
