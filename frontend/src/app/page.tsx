"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { Splash } from "@/components/brand/logo";
import { Landing } from "@/components/landing/landing";
import { useAuth } from "@/lib/auth/store";

/**
 * The public front door. Signed-in people go straight to their invoices, exactly as before; anyone else sees the landing
 * page, which links to the sign-in and registration pages. While the session is still being restored nothing is shown
 * but the splash, so a signed-in visitor never sees the landing page flash by.
 */
export default function Home() {
  const router = useRouter();
  const status = useAuth((s) => s.status);

  useEffect(() => {
    if (status === "authenticated") router.replace("/invoices");
  }, [status, router]);

  if (status !== "anonymous") return <Splash />;
  return <Landing />;
}
