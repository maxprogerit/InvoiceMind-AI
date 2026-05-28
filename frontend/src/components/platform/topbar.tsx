"use client";

import { useState } from "react";
import { useTheme } from "next-themes";
import { Button } from "@/components/ui/button";
import { useUiStore } from "@/store/ui-store";

export function Topbar() {
  const { theme, setTheme } = useTheme();
  const workspace = useUiStore((s) => s.workspace);
  const setWorkspace = useUiStore((s) => s.setWorkspace);
  const [uploadOpen, setUploadOpen] = useState(false);

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-border/60 bg-background/70 px-4 backdrop-blur-xl lg:px-8">
      <div>
        <p className="text-xs text-cyan-300/70">Workspace</p>
        <select
          value={workspace}
          onChange={(e) => setWorkspace(e.target.value)}
          className="mt-1 rounded-lg border border-border/60 bg-black/20 px-2 py-1 text-sm font-semibold text-foreground outline-none"
        >
          <option>Global Finance</option>
          <option>North America AP</option>
          <option>EMEA Operations</option>
        </select>
      </div>
      <div className="flex items-center gap-2">
        <input
          placeholder="Global search invoices, vendors, workflows..."
          className="hidden h-10 w-80 rounded-xl border border-border/60 bg-black/20 px-3 text-sm text-foreground outline-none xl:block"
        />
        <Button onClick={() => setUploadOpen(true)}>Upload</Button>
        <Button onClick={() => setTheme(theme === "dark" ? "light" : "dark")}>Theme</Button>
      </div>
      {uploadOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-lg rounded-2xl border border-border/60 bg-card p-6">
            <h3 className="text-lg font-semibold">Upload documents</h3>
            <p className="mt-1 text-sm text-slate-400">Drop invoices, receipts, or enterprise docs for AI extraction.</p>
            <div className="mt-4 rounded-xl border border-dashed border-cyan-300/40 p-8 text-center text-sm">
              Drag and drop files here
            </div>
            <div className="mt-4 flex justify-end">
              <Button onClick={() => setUploadOpen(false)}>Close</Button>
            </div>
          </div>
        </div>
      ) : null}
    </header>
  );
}
