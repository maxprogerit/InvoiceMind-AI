"use client";

import { useEffect, useRef, useCallback } from "react";
import { useRealtimeStore } from "@/store/realtime-store";
import { useQueryClient } from "@tanstack/react-query";

// Minimal STOMP frame parser (no library needed — STOMP is plain text)
function parseStompFrame(raw: string) {
  const nullIdx = raw.indexOf("\0");
  const body = nullIdx > -1 ? raw.substring(0, nullIdx) : raw;
  const lines = body.split("\n");
  const command = lines[0]?.trim();
  const headers: Record<string, string> = {};
  let i = 1;
  while (i < lines.length && lines[i].trim() !== "") {
    const colon = lines[i].indexOf(":");
    if (colon > -1) {
      headers[lines[i].substring(0, colon).trim()] = lines[i].substring(colon + 1).trim();
    }
    i++;
  }
  const bodyStr = lines.slice(i + 1).join("\n").replace(/\0$/, "");
  return { command, headers, body: bodyStr };
}

function buildStompFrame(command: string, headers: Record<string, string>, body = "") {
  const headerStr = Object.entries(headers).map(([k, v]) => `${k}:${v}`).join("\n");
  return `${command}\n${headerStr}\n\n${body}\0`;
}

const WS_URL = (process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080/ws/live");

export function useWebSocket() {
  const wsRef = useRef<WebSocket | null>(null);
  const heartbeatRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const reconnectRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mountedRef = useRef(true);

  const { addEvent } = useRealtimeStore();
  const qc = useQueryClient();

  const handleMessage = useCallback((payload: string) => {
    let data: Record<string, unknown>;
    try { data = JSON.parse(payload); } catch { return; }

    const type = (data.type ?? data.eventType ?? data.status) as string | undefined;

    // Add to realtime store
    addEvent({ type: type ?? "update", payload: data, timestamp: Date.now() });

    // Invalidate relevant React Query caches based on event type
    if (!type) return;
    const t = type.toUpperCase();

    if (t.includes("DASHBOARD")) {
      qc.invalidateQueries({ queryKey: ["dashboard-overview"] });
    }
    if (t.includes("OCR") || t.includes("DOCUMENT") || t.includes("UPLOAD")) {
      qc.invalidateQueries({ queryKey: ["documents"] });
      qc.invalidateQueries({ queryKey: ["dashboard-overview"] });
    }
    if (t.includes("EXECUTION") || t.includes("PROCESSING")) {
      qc.invalidateQueries({ queryKey: ["executions"] });
      qc.invalidateQueries({ queryKey: ["dashboard-overview"] });
    }
    if (t.includes("APPROVAL")) {
      qc.invalidateQueries({ queryKey: ["approvals"] });
      qc.invalidateQueries({ queryKey: ["dashboard-overview"] });
    }
    if (t.includes("INVOICE")) {
      qc.invalidateQueries({ queryKey: ["invoices"] });
    }
  }, [addEvent, qc]);

  const connect = useCallback(() => {
    if (!mountedRef.current) return;
    if (typeof window === "undefined") return;

    const token = localStorage.getItem("invoicemind-token");
    // Only connect if authenticated
    if (!token) return;

    const ws = new WebSocket(WS_URL);
    wsRef.current = ws;

    ws.onopen = () => {
      // Send STOMP CONNECT frame
      ws.send(buildStompFrame("CONNECT", {
        "accept-version": "1.1,1.0",
        "heart-beat": "10000,10000",
        "Authorization": `Bearer ${token}`,
      }));
    };

    ws.onmessage = (event) => {
      const frame = parseStompFrame(event.data as string);
      if (frame.command === "CONNECTED") {
        // Subscribe to topics after successful STOMP connection
        ws.send(buildStompFrame("SUBSCRIBE", { id: "sub-0", destination: "/topic/executions" }));
        ws.send(buildStompFrame("SUBSCRIBE", { id: "sub-1", destination: "/topic/dashboard" }));

        // Start heartbeat
        heartbeatRef.current = setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) ws.send("\n");
        }, 10000);
      } else if (frame.command === "MESSAGE") {
        handleMessage(frame.body);
      } else if (frame.command === "ERROR") {
        console.warn("[WS] STOMP error:", frame.body);
      }
    };

    ws.onerror = () => { /* silent — will reconnect via onclose */ };

    ws.onclose = () => {
      if (heartbeatRef.current) clearInterval(heartbeatRef.current);
      if (!mountedRef.current) return;
      // Reconnect after 5s
      reconnectRef.current = setTimeout(connect, 5000);
    };
  }, [handleMessage]);

  useEffect(() => {
    mountedRef.current = true;
    connect();
    return () => {
      mountedRef.current = false;
      if (heartbeatRef.current) clearInterval(heartbeatRef.current);
      if (reconnectRef.current) clearTimeout(reconnectRef.current);
      if (wsRef.current) {
        wsRef.current.onclose = null; // prevent reconnect on intentional close
        wsRef.current.close();
      }
    };
  }, [connect]);
}
