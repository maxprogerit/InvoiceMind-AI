import { Badge } from "@/components/ui/badge";
import type { InvoiceSummary } from "@/lib/api/types";

/** Small list markers for the AP risks: duplicates and vendors nobody has confirmed yet. */
export function TrustFlags({ invoice }: { invoice: Pick<InvoiceSummary, "duplicateStatus" | "vendorVerified" | "status"> }) {
  const final = invoice.status === "APPROVED" || invoice.status === "REJECTED";
  return (
    <>
      {invoice.duplicateStatus === "DUPLICATE" && <Badge tone="bad">duplicate</Badge>}
      {invoice.duplicateStatus === "POSSIBLE_DUPLICATE" && <Badge tone="warn">possible duplicate</Badge>}
      {!invoice.vendorVerified && !final && <Badge tone="info">new vendor</Badge>}
    </>
  );
}
