"use client";

import { useEffect } from "react";

import { Button } from "@/components/ui/button";

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <div className="grid min-h-dvh place-items-center px-6 text-center">
      <div>
        <p className="font-mono text-sm uppercase tracking-[0.3em] text-bad">Error</p>
        <h1 className="mt-4 text-4xl font-bold tracking-tight">Something went wrong</h1>
        <p className="mt-3 max-w-md text-muted">An unexpected error occurred in the interface. Your data is safe; try again.</p>
        <Button onClick={reset} className="mt-8" size="lg">
          Try again
        </Button>
      </div>
    </div>
  );
}
