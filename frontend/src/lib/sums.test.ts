import { describe, expect, it } from "vitest";

import { isConsistent, parseAmount, sumChecks } from "./sums";

describe("live arithmetic checks", () => {
  it("passes consistent invoices", () => {
    const checks = sumChecks({ subtotal: "1200.00", tax: "228.00", total: "1428.00", lineItems: [{ total: "1000.00" }, { total: "200.00" }] });
    expect(checks).toHaveLength(2);
    expect(checks.every(isConsistent)).toBe(true);
  });

  it("flags subtotal + tax != total with the expected value", () => {
    const [check] = sumChecks({ subtotal: "1000", tax: "190", total: "1500" });
    expect(check?.label).toBe("Subtotal + tax = total");
    expect(check?.expected).toBe(1190);
    expect(isConsistent(check!)).toBe(false);
  });

  it("allows one cent of rounding on the total and one cent per line on the line sum", () => {
    expect(isConsistent(sumChecks({ subtotal: "100.00", tax: "19.00", total: "119.01" })[0]!)).toBe(true);
    expect(isConsistent(sumChecks({ subtotal: "100.00", tax: "19.00", total: "119.02" })[0]!)).toBe(false);
    const lines = sumChecks({ subtotal: "30.02", lineItems: [{ total: "10.00" }, { total: "10.00" }, { total: "10.00" }] });
    expect(isConsistent(lines[0]!)).toBe(true); // 3 lines -> 0.03 tolerance
  });

  it("treats a missing tax as zero and skips checks it cannot compute", () => {
    expect(isConsistent(sumChecks({ subtotal: "50", total: "50" })[0]!)).toBe(true);
    expect(sumChecks({})).toEqual([]);
    expect(sumChecks({ subtotal: "50", lineItems: [{ total: "" }] })).toEqual([]); // incomplete table
  });

  it("parses amounts defensively", () => {
    expect(parseAmount("12.50")).toBe(12.5);
    expect(parseAmount("")).toBeNull();
    expect(parseAmount("abc")).toBeNull();
    expect(parseAmount(undefined)).toBeNull();
  });
});
