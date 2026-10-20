"use client";

import { Building2, Check, ChevronsUpDown, Search } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { useId, useMemo, useState } from "react";
import { toast } from "sonner";

import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useOrganizations, useSwitchOrganization } from "@/lib/api/hooks";
import { useAuth } from "@/lib/auth/store";
import { cn } from "@/lib/utils";

/**
 * Picks the client company the session works in. Only people who can reach more than one company see it (everybody else
 * gets `fallback`). Choosing a company asks the server for a token for it; the token, not this component, decides
 * what the person may see.
 */
export function ClientSwitcher({ fallback = null, onSwitched }: { fallback?: React.ReactNode; onSwitched?: () => void }) {
  const router = useRouter();
  const pathname = usePathname();
  const current = useAuth((s) => s.user);
  const { data: options } = useOrganizations();
  const switchOrg = useSwitchOrganization();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [highlight, setHighlight] = useState(0);
  const listId = useId();

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (options ?? []).filter((o) => o.name.toLowerCase().includes(q));
  }, [options, query]);

  if (!options || options.length < 2) return <>{fallback}</>;

  function choose(id: string, name: string) {
    if (id === current?.organizationId) {
      setOpen(false);
      return;
    }
    switchOrg.mutate(id, {
      onSuccess: () => {
        toast.success(`Now working in ${name}`);
        setOpen(false);
        setQuery("");
        onSwitched?.();
        // an open document belongs to the previous company and would only answer 404 now
        if (pathname.startsWith("/invoices/")) router.push("/invoices");
      },
      onError: (e) => toast.error(e.message),
    });
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setHighlight((h) => Math.min(h + 1, shown.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      const o = shown[highlight];
      if (o) choose(o.id, o.name);
    }
  }

  return (
    <Popover
      open={open}
      onOpenChange={(o) => {
        setOpen(o);
        if (o) {
          setQuery("");
          setHighlight(0);
        }
      }}
    >
      <PopoverTrigger
        className="mt-2 flex w-full items-center gap-2 rounded-control border border-line bg-bg/50 px-2.5 py-2 text-left transition-colors hover:border-line-strong"
        aria-label={`Client company: ${current?.organizationName ?? ""}. Change`}
        aria-haspopup="listbox"
      >
        <Building2 className="size-4 shrink-0 text-dim" aria-hidden />
        <span className="min-w-0 flex-1 truncate text-sm font-medium">{current?.organizationName}</span>
        <ChevronsUpDown className="size-3.5 shrink-0 text-dim" aria-hidden />
      </PopoverTrigger>
      <PopoverContent align="start" className="w-64">
        <div className="flex items-center gap-2 border-b border-line px-3">
          <Search className="size-4 shrink-0 text-dim" aria-hidden />
          <input
            autoFocus
            role="combobox"
            aria-expanded="true"
            aria-controls={listId}
            aria-activedescendant={shown[highlight] ? `${listId}-${shown[highlight].id}` : undefined}
            aria-label="Search client companies"
            placeholder="Search clients…"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setHighlight(0);
            }}
            onKeyDown={onKeyDown}
            className="h-10 w-full bg-transparent text-sm placeholder:text-dim focus:outline-none"
          />
        </div>
        <ul id={listId} role="listbox" aria-label="Client companies" className="max-h-72 overflow-y-auto p-1.5">
          {shown.length === 0 && <li className="px-3 py-4 text-center text-sm text-dim">No client matches “{query}”</li>}
          {shown.map((o, i) => {
            const selected = o.id === current?.organizationId;
            return (
              <li key={o.id} role="presentation">
                <button
                  id={`${listId}-${o.id}`}
                  type="button"
                  role="option"
                  aria-selected={selected}
                  disabled={switchOrg.isPending}
                  onClick={() => choose(o.id, o.name)}
                  onMouseEnter={() => setHighlight(i)}
                  className={cn(
                    "flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left text-sm transition-colors",
                    i === highlight ? "bg-surface" : "",
                    selected ? "text-accent" : "text-fg",
                  )}
                >
                  <span className="min-w-0 flex-1 truncate">{o.name}</span>
                  <span className="font-mono text-[10px] capitalize text-dim">{o.role.toLowerCase()}</span>
                  {selected && <Check className="size-4 shrink-0" aria-hidden />}
                </button>
              </li>
            );
          })}
        </ul>
      </PopoverContent>
    </Popover>
  );
}
