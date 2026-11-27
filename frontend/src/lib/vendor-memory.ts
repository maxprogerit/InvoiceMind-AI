import type { VendorMemory } from "@/lib/api/types";

/** One learned rule in words: "when the document says USD -> CAD", "no due date -> invoice date + 30 days". */
export function describeMemory(m: VendorMemory): string {
  switch (m.field) {
    case "CURRENCY":
      return m.whenExtracted ? `Currency read as ${m.whenExtracted} → ${m.value}` : `No currency on the document → ${m.value}`;
    case "PAYMENT_TERMS":
      return `No due date on the document → invoice date + ${m.value} days`;
    case "CATEGORY":
      return `Expense category → ${m.value}`;
    default:
      return `${m.field} → ${m.value}`;
  }
}
