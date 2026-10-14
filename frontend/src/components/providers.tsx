"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Toaster } from "sonner";

import { ApiError, refreshSession } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/store";

import { TooltipProvider } from "./ui/tooltip";

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: true,
        // client errors (401/403/404/409...) will not fix themselves; only retry server/network failures
        retry: (count, error) => !(error instanceof ApiError && error.status > 0 && error.status < 500) && count < 2,
      },
    },
  });
}

/** Restores the session once on load from the httpOnly refresh cookie. */
function AuthBootstrap() {
  useEffect(() => {
    const { status, setStatus } = useAuth.getState();
    if (status !== "idle") return;
    setStatus("loading");
    void refreshSession().then((ok) => {
      if (!ok) useAuth.getState().clear();
    });
  }, []);
  return null;
}

export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(makeQueryClient);
  return (
    <QueryClientProvider client={client}>
      <TooltipProvider delayDuration={200}>
        <AuthBootstrap />
        {children}
        <Toaster
          theme="dark"
          position="bottom-right"
          toastOptions={{
            classNames: {
              toast: "!bg-surface-2 !border !border-line-strong !text-fg !font-sans",
              description: "!text-muted",
            },
          }}
        />
      </TooltipProvider>
    </QueryClientProvider>
  );
}
