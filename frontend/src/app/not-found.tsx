import Link from "next/link";

import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <div className="grid min-h-dvh place-items-center px-6 text-center">
      <div>
        <p className="font-mono text-sm uppercase tracking-[0.3em] text-accent">404</p>
        <h1 className="mt-4 text-5xl font-bold tracking-tight">Page not found</h1>
        <p className="mt-3 text-muted">That page does not exist or has moved.</p>
        <Button asChild className="mt-8" size="lg">
          <Link href="/invoices">Back to invoices</Link>
        </Button>
      </div>
    </div>
  );
}
