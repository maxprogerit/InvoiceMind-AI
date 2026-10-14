/** Live arithmetic hints on the review form. They mirror the backend rules; the server remains the authority. */

export interface SumFormValues {
  subtotal?: string;
  tax?: string;
  total?: string;
  lineItems?: ({ total?: string } | undefined)[];
}

export interface SumCheck {
  label: string;
  expected: number;
  actual: number;
  tolerance: number;
}

export function parseAmount(v: string | undefined): number | null {
  if (v === undefined || v.trim() === "" || !Number.isFinite(Number(v))) return null;
  return Number(v);
}

export function isConsistent(c: SumCheck): boolean {
  return Math.abs(c.expected - c.actual) <= c.tolerance + 1e-9;
}

export function sumChecks(v: SumFormValues): SumCheck[] {
  const checks: SumCheck[] = [];
  const lines = (v.lineItems ?? []).map((l) => parseAmount(l?.total));
  const subtotal = parseAmount(v.subtotal);
  const tax = parseAmount(v.tax) ?? 0;
  const total = parseAmount(v.total);
  if (lines.length > 0 && lines.every((l): l is number => l !== null) && subtotal !== null) {
    // one cent of rounding per line, like the backend
    checks.push({ label: "Line items = subtotal", expected: lines.reduce((a, b) => a + b, 0), actual: subtotal, tolerance: 0.01 * Math.max(1, lines.length) });
  }
  if (subtotal !== null && total !== null) {
    checks.push({ label: "Subtotal + tax = total", expected: subtotal + tax, actual: total, tolerance: 0.01 });
  }
  return checks;
}
