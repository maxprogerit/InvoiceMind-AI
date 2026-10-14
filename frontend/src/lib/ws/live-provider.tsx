"use client";

import { Client } from "@stomp/stompjs";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { toast } from "sonner";

import { getValidToken } from "@/lib/api/client";
import { isReviewer, type NotificationItem, type StatusEvent } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/store";

import { useLive } from "./live-store";

function brokerUrl(): string {
  const configured = process.env.NEXT_PUBLIC_WS_URL;
  if (configured) return configured;
  const scheme = window.location.protocol === "https:" ? "wss" : "ws";
  return `${scheme}://${window.location.host}/ws/live`;
}

/**
 * Keeps one STOMP connection open while signed in. Server pushes drive cache invalidation, so lists,
 * detail pages and analytics update without polling. The connection authenticates in the CONNECT frame
 * with a fresh access token on every (re)connect.
 */
export function LiveProvider({ children }: { children: React.ReactNode }) {
  const qc = useQueryClient();
  const router = useRouter();
  const user = useAuth((s) => s.user);
  const userId = user?.id;
  const organizationId = user?.organizationId;
  const reviewer = isReviewer(user?.role);

  useEffect(() => {
    if (!userId) return;
    const { setConnected, apply } = useLive.getState();

    const client = new Client({
      brokerURL: brokerUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 15000,
      heartbeatOutgoing: 15000,
      beforeConnect: async () => {
        const token = await getValidToken();
        client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      },
      onConnect: () => {
        setConnected(true);

        const onStatus = (body: string, own: boolean) => {
          const e = JSON.parse(body) as StatusEvent;
          apply(e);
          void qc.invalidateQueries({ queryKey: ["invoices"] });
          void qc.invalidateQueries({ queryKey: ["analytics"] });
          void qc.invalidateQueries({ queryKey: ["invoice", e.invoiceId] });
          if (own && (e.status === "NEEDS_REVIEW" || e.status === "VALIDATED" || e.status === "FAILED")) {
            const label = e.invoiceNumber ? `Invoice ${e.invoiceNumber}` : "Your invoice";
            const action = { label: "Open", onClick: () => router.push(`/invoices/${e.invoiceId}`) };
            if (e.status === "FAILED") toast.error(`${label} failed`, { description: e.failureReason, action });
            else if (e.status === "NEEDS_REVIEW") toast.warning(`${label} needs review`, { action });
            else toast.success(`${label} is ready`, { action });
          }
        };

        client.subscribe("/user/queue/invoices", (m) => onStatus(m.body, true));
        if (reviewer && organizationId) {
          client.subscribe(`/topic/org/${organizationId}/reviewers`, (m) => onStatus(m.body, false));
        }
        client.subscribe("/user/queue/notifications", (m) => {
          const n = JSON.parse(m.body) as NotificationItem;
          void qc.invalidateQueries({ queryKey: ["notifications"] });
          if (n.type === "AWAITING_APPROVAL" || n.type === "APPROVED" || n.type === "REJECTED") {
            toast.info(n.title, { description: n.body });
          }
        });
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });
    client.activate();

    return () => {
      setConnected(false);
      void client.deactivate();
    };
  }, [userId, organizationId, reviewer, qc, router]);

  return children;
}
