export function uid(prefix: string): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 9)}`;
}

export function currency(value: number, code = "USD"): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: code,
    maximumFractionDigits: 2,
  }).format(value);
}

export function byQuery<T>(items: T[], query: string, pick: (item: T) => string): T[] {
  if (!query.trim()) return items;
  const normalized = query.toLowerCase();
  return items.filter((item) => pick(item).toLowerCase().includes(normalized));
}
