"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { Splash } from "@/components/brand/logo";
import { Hero } from "@/components/auth/hero";
import { useAuth } from "@/lib/auth/store";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const status = useAuth((s) => s.status);

  useEffect(() => {
    if (status === "authenticated") router.replace("/invoices");
  }, [status, router]);

  if (status === "idle" || status === "loading" || status === "authenticated") return <Splash />;

  return (
    <div className="relative min-h-dvh overflow-hidden">
      <div className="fixed inset-x-0 top-0 z-10 h-2 bg-accent" aria-hidden />
      <div className="bg-grid pointer-events-none absolute inset-0" aria-hidden />
      <div
        className="pointer-events-none absolute -left-40 top-1/3 size-[36rem] rounded-full bg-accent/10 blur-[120px]"
        aria-hidden
      />
      <main className="relative grid min-h-dvh lg:grid-cols-[1.1fr_1fr]">
        <Hero />
        <div className="flex items-center justify-center px-6 pb-16 pt-4 sm:px-10 lg:py-20">{children}</div>
      </main>
    </div>
  );
}
