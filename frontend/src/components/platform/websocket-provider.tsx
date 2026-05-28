"use client";

import { useWebSocket } from "@/hooks/use-websocket";

export function WebSocketProvider() {
  useWebSocket();
  return null;
}
