"use client";

import { Bell, BellOff, CheckCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/misc";
import { useMarkRead, useNotifications } from "@/lib/api/hooks";
import { timeAgo } from "@/lib/format";
import { cn } from "@/lib/utils";

const TYPE_DOT: Record<string, string> = {
  NEEDS_REVIEW: "bg-warn",
  AWAITING_APPROVAL: "bg-accent",
  PROCESSED: "bg-ok",
  APPROVED: "bg-ok",
  REJECTED: "bg-bad",
  FAILED: "bg-bad",
};

export function NotificationBell() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const { data, isLoading } = useNotifications();
  const markRead = useMarkRead();
  const unread = data?.unreadCount ?? 0;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        className="relative rounded-control border border-transparent p-2.5 text-muted transition-colors hover:bg-surface-2 hover:text-fg"
        aria-label={unread ? `Notifications, ${unread} unread` : "Notifications"}
      >
        <Bell className="size-[18px]" />
        {unread > 0 && (
          <span className="absolute right-1.5 top-1.5 grid min-w-4 place-items-center rounded-full bg-accent px-1 font-mono text-[10px] font-bold leading-4 text-accent-fg">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </PopoverTrigger>
      <PopoverContent className="w-[22rem]">
        <div className="flex items-center justify-between border-b border-line px-4 py-3">
          <span className="label-mono">Notifications</span>
          <button
            onClick={() => markRead.mutate(undefined)}
            disabled={unread === 0}
            className="flex items-center gap-1.5 font-mono text-[11px] text-muted transition-colors hover:text-accent disabled:opacity-40"
          >
            <CheckCheck className="size-3.5" /> Mark all read
          </button>
        </div>
        <div className="max-h-96 overflow-y-auto">
          {isLoading && (
            <div className="space-y-3 p-4">
              <Skeleton className="h-10" />
              <Skeleton className="h-10" />
            </div>
          )}
          {!isLoading && (data?.items.length ?? 0) === 0 && (
            <div className="flex flex-col items-center gap-2 px-6 py-10 text-center text-sm text-dim">
              <BellOff className="size-6" />
              Nothing yet. You will be notified when an invoice needs you.
            </div>
          )}
          {data?.items.map((n) => (
            <button
              key={n.id}
              onClick={() => {
                if (!n.read) markRead.mutate(n.id);
                setOpen(false);
                if (n.invoiceId) router.push(`/invoices/${n.invoiceId}`);
              }}
              className={cn(
                "flex w-full gap-3 border-b border-line/60 px-4 py-3 text-left transition-colors last:border-0 hover:bg-surface-3",
                !n.read && "bg-accent-soft/30",
              )}
            >
              <span className={cn("mt-1.5 size-2 shrink-0 rounded-full", n.read ? "bg-line-strong" : (TYPE_DOT[n.type] ?? "bg-accent"))} aria-hidden />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{n.title}</span>
                <span className="mt-0.5 line-clamp-2 block text-xs text-muted">{n.body}</span>
                <span className="mt-1 block font-mono text-[10px] text-dim">{timeAgo(n.createdAt)}</span>
              </span>
            </button>
          ))}
        </div>
      </PopoverContent>
    </Popover>
  );
}
