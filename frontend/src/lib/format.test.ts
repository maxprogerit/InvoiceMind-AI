import { describe, expect, it } from "vitest";

import {
  extractCheckLabel,
  formatAmount,
  formatBytes,
  formatDate,
  formatDuration,
  formatMoney,
  formatPercent,
  initials,
  textSourceLabel,
} from "./format";

describe("formatting", () => {
  it("formats money with a currency symbol and falls back for unknown currencies", () => {
    expect(formatMoney(1428, "EUR")).toBe("€1,428.00");
    expect(formatMoney("92.3", "USD")).toBe("$92.30");
    expect(formatMoney(10, "ZZZ_NOPE")).toBe("10.00");
    expect(formatMoney(10, null)).toBe("10.00");
  });

  it("shows a dash for missing or non-numeric values instead of NaN", () => {
    expect(formatMoney(null, "EUR")).toBe("—");
    expect(formatMoney(undefined)).toBe("—");
    expect(formatMoney("abc", "EUR")).toBe("—");
    expect(formatAmount(null)).toBe("—");
    expect(formatAmount(1240)).toBe("1,240.00");
    expect(formatPercent(undefined)).toBe("—");
    expect(formatDuration(null)).toBe("—");
    expect(formatDate(null)).toBe("—");
    expect(formatDate("not a date")).toBe("—");
  });

  it("formats dates, sizes, percentages and durations", () => {
    expect(formatDate("2026-03-14")).toBe("2026-03-14");
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(2048)).toBe("2.0 KB");
    expect(formatBytes(5 * 1024 * 1024)).toBe("5.0 MB");
    expect(formatPercent(0.9293, 1)).toBe("92.9%");
    expect(formatDuration(0.4)).toBe("0.4s");
    expect(formatDuration(125)).toBe("2m 5s");
  });

  it("builds initials", () => {
    expect(initials("Demo Admin")).toBe("DA");
    expect(initials("  single  ")).toBe("S");
    expect(initials("a b c")).toBe("AB");
  });
});

describe("extract-service labels", () => {
  it("names the text source", () => {
    expect(textSourceLabel("text_layer")).toBe("PDF text layer");
    expect(textSourceLabel("ocr")).toBe("OCR (scan or photo)");
    expect(textSourceLabel("mixed")).toBe("Text layer + OCR");
    expect(textSourceLabel(null)).toBe("—");
  });

  it("summarises the first-pass check", () => {
    expect(extractCheckLabel({ status: "valid", errors: 0, warnings: 0 })).toBe("valid");
    expect(extractCheckLabel({ status: "needs_review", errors: 1, warnings: 2 })).toBe("needs review · 1 error, 2 warnings");
    expect(extractCheckLabel(null)).toBe("—");
  });
});
