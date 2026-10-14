"use client";

import { Menu } from "lucide-react";
import Link from "next/link";
import { Dialog as DialogPrimitive } from "radix-ui";
import { useState } from "react";

import { Logo } from "@/components/brand/logo";

import { NotificationBell } from "./notification-bell";
import { NavLinks, UserPanel } from "./sidebar";

export function MobileBar() {
  const [open, setOpen] = useState(false);
  return (
    <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-line bg-bg/90 px-4 backdrop-blur lg:hidden">
      <Link href="/invoices" aria-label="InvoiceMind home">
        <Logo className="text-xl" />
      </Link>
      <div className="flex items-center gap-1">
        <NotificationBell />
        <DialogPrimitive.Root open={open} onOpenChange={setOpen}>
          <DialogPrimitive.Trigger className="rounded-md p-2 text-muted hover:bg-surface-2 hover:text-fg" aria-label="Open menu">
            <Menu className="size-5" />
          </DialogPrimitive.Trigger>
          <DialogPrimitive.Portal>
            <DialogPrimitive.Overlay className="fixed inset-0 z-40 bg-black/70" />
            <DialogPrimitive.Content className="fixed inset-y-0 left-0 z-50 flex w-72 flex-col gap-6 border-r border-line bg-bg px-5 py-6 focus:outline-none">
              <DialogPrimitive.Title className="sr-only">Navigation</DialogPrimitive.Title>
              <Logo />
              <div className="flex-1 overflow-y-auto">
                <NavLinks onNavigate={() => setOpen(false)} />
              </div>
              <UserPanel />
            </DialogPrimitive.Content>
          </DialogPrimitive.Portal>
        </DialogPrimitive.Root>
      </div>
    </header>
  );
}
