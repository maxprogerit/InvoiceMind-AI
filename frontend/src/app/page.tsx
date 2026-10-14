"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { Splash } from "@/components/brand/logo";
import { useAuth } from "@/lib/auth/store";

/** The root only routes: signed-in users go to their invoices, everyone else to the sign-in page. */
export default function Home() {
  const router = useRouter();
  const status = useAuth((s) => s.status);

  useEffect(() => {
    if (status === "authenticated") router.replace("/invoices");
    if (status === "anonymous") router.replace("/login");
  }, [status, router]);

  return <Splash />;
}
