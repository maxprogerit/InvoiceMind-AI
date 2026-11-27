import { describe, expect, it } from "vitest";

import { describeMemory } from "./vendor-memory";

const base = { id: 1, learnedAt: "2026-05-01T10:00:00Z", timesApplied: 0 };

describe("vendor memory in words", () => {
  it("describes each kind of learned rule", () => {
    expect(describeMemory({ ...base, field: "CURRENCY", whenExtracted: "USD", value: "CAD" })).toBe("Currency read as USD → CAD");
    expect(describeMemory({ ...base, field: "CURRENCY", value: "EUR" })).toBe("No currency on the document → EUR");
    expect(describeMemory({ ...base, field: "PAYMENT_TERMS", value: "30" })).toBe("No due date on the document → invoice date + 30 days");
    expect(describeMemory({ ...base, field: "CATEGORY", value: "Hosting" })).toBe("Expense category → Hosting");
  });
});
