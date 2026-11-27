import { format, formatDistanceToNowStrict, parseISO } from "date-fns";

/** Locale is fixed so server- and client-rendered numbers never differ (no hydration mismatch). */
const LOCALE = "en-US";

export function formatMoney(amount: number | string | null | undefined, currency?: string | null): string {
  if (amount === null || amount === undefined || amount === "") return "—";
  const n = Number(amount);
  if (!Number.isFinite(n)) return "—";
  if (currency && /^[A-Za-z]{3}$/.test(currency)) {
    try {
      return new Intl.NumberFormat(LOCALE, { style: "currency", currency: currency.toUpperCase() }).format(n);
    } catch {
      /* unknown currency code: fall through to a plain number */
    }
  }
  return new Intl.NumberFormat(LOCALE, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n);
}

/** Plain amount without currency symbol, e.g. 1,240.00 (used in dense tables next to a currency column). */
export function formatAmount(amount: number | string | null | undefined): string {
  if (amount === null || amount === undefined || amount === "") return "—";
  const n = Number(amount);
  return Number.isFinite(n)
    ? new Intl.NumberFormat(LOCALE, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)
    : "—";
}

export function formatCompactMoney(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat(LOCALE, { style: "currency", currency, notation: "compact", maximumFractionDigits: 1 }).format(amount);
  } catch {
    return new Intl.NumberFormat(LOCALE, { notation: "compact", maximumFractionDigits: 1 }).format(amount);
  }
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  const d = parseISO(value);
  return Number.isNaN(d.getTime()) ? "—" : format(d, "yyyy-MM-dd");
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const d = parseISO(value);
  return Number.isNaN(d.getTime()) ? "—" : format(d, "yyyy-MM-dd HH:mm");
}

export function timeAgo(value: string | null | undefined): string {
  if (!value) return "";
  const d = parseISO(value);
  return Number.isNaN(d.getTime()) ? "" : `${formatDistanceToNowStrict(d)} ago`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function formatPercent(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return "—";
  return `${(value * 100).toFixed(digits)}%`;
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds)) return "—";
  return seconds < 60 ? `${seconds.toFixed(1)}s` : `${Math.floor(seconds / 60)}m ${Math.round(seconds % 60)}s`;
}

export function formatNumber(n: number): string {
  return new Intl.NumberFormat(LOCALE).format(n);
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
}

/** How the extract-service obtained the text: the PDF's own text layer, OCR of an image, or both. */
export function textSourceLabel(source: string | null | undefined): string {
  switch (source) {
    case "text_layer":
      return "PDF text layer";
    case "ocr":
      return "OCR (scan or photo)";
    case "mixed":
      return "Text layer + OCR";
    default:
      return "—";
  }
}

/** One line for the extract-service's own first-pass check, e.g. "needs review · 1 error, 2 warnings". */
export function extractCheckLabel(check: { status: string; errors: number; warnings: number } | null | undefined): string {
  if (!check) return "—";
  const status = check.status.replace("_", " ");
  const parts: string[] = [];
  if (check.errors) parts.push(`${check.errors} error${check.errors === 1 ? "" : "s"}`);
  if (check.warnings) parts.push(`${check.warnings} warning${check.warnings === 1 ? "" : "s"}`);
  return parts.length ? `${status} · ${parts.join(", ")}` : status;
}
