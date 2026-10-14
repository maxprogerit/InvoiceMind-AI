"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";

import { Splash } from "@/components/brand/logo";
import { MobileBar } from "@/components/layout/mobile-nav";
import { Sidebar } from "@/components/layout/sidebar";
import { useAuth } from "@/lib/auth/store";
import { LiveProvider } from "@/lib/ws/live-provider";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const status = useAuth((s) => s.status);

  useEffect(() => {
    if (status === "anonymous") router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [status, router, pathname]);

  if (status !== "authenticated") return <Splash />;

  return (
    <LiveProvider>
      <div className="flex min-h-dvh">
        <Sidebar />
        <div className="min-w-0 flex-1">
          <MobileBar />
          <main className="mx-auto w-full max-w-[1400px] px-4 py-6 sm:px-8 sm:py-9">{children}</main>
        </div>
      </div>
    </LiveProvider>
  );
}
