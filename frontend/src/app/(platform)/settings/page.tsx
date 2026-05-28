"use client";

import { useState } from "react";
import { useQuery, useMutation } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useNotificationStore } from "@/store/notification-store";
import { settingsApi } from "@/services/settings-api";

export default function SettingsPage() {
  const { success, error } = useNotificationStore();
  const [tab, setTab] = useState<"profile" | "org" | "apikeys">("profile");

  const { data: profile, isLoading: loadProfile } = useQuery({
    queryKey: ["settings-profile"],
    queryFn: settingsApi.profile,
  });
  const { data: org, isLoading: loadOrg } = useQuery({
    queryKey: ["settings-org"],
    queryFn: settingsApi.org,
  });
  const { data: keys, isLoading: loadKeys } = useQuery({
    queryKey: ["settings-apikeys"],
    queryFn: settingsApi.apiKeys,
  });

  const [profileForm, setProfileForm] = useState<Record<string, string>>({});
  const [orgForm, setOrgForm] = useState<Record<string, string>>({});

  const profileMutation = useMutation({
    mutationFn: () => settingsApi.updateProfile(profileForm as any),
    onSuccess: () => success("Profile saved"),
    onError: (e: Error) => error(e.message),
  });
  const orgMutation = useMutation({
    mutationFn: () => settingsApi.updateOrg(orgForm),
    onSuccess: () => success("Organization settings saved"),
    onError: (e: Error) => error(e.message),
  });

  const tabs = [
    { id: "profile" as const, label: "Profile" },
    { id: "org" as const, label: "Organization" },
    { id: "apikeys" as const, label: "API Keys" },
  ];

  return (
    <PageShell title="Settings" subtitle="Organization controls, users, permissions, APIs, OCR, AI models, and integrations.">
      <div className="flex gap-2 mb-6">
        {tabs.map((t) => (
          <button key={t.id} onClick={() => setTab(t.id)}
            className={`rounded-xl px-4 py-2 text-sm font-medium transition-colors ${
              tab === t.id ? "bg-cyan-500/20 text-cyan-300" : "text-slate-400 hover:text-slate-200"
            }`}>
            {t.label}
          </button>
        ))}
      </div>

      {tab === "profile" && (
        <div className="max-w-lg space-y-4">
          {loadProfile ? <Skeleton className="h-48 w-full" /> : (
            <>
              {[
                { k: "fullName", label: "Full Name", val: profileForm.fullName ?? (profile?.fullName ?? profile?.name ?? "") },
                { k: "email", label: "Email", val: profileForm.email ?? (profile?.email ?? "") },
              ].map(({ k, label, val }) => (
                <div key={k}>
                  <label className="mb-1 block text-xs text-slate-400">{label}</label>
                  <input value={val} onChange={(e) => setProfileForm((f) => ({ ...f, [k]: e.target.value }))}
                    className="w-full rounded-xl border border-border/60 bg-slate-800 px-3 py-2 text-sm text-foreground" />
                </div>
              ))}
              <Button onClick={() => profileMutation.mutate()} disabled={profileMutation.isPending}>
                {profileMutation.isPending ? "Saving…" : "Save Profile"}
              </Button>
            </>
          )}
        </div>
      )}

      {tab === "org" && (
        <div className="max-w-lg space-y-4">
          {loadOrg ? <Skeleton className="h-48 w-full" /> : (
            <>
              {[
                { k: "name", label: "Organization Name", val: orgForm.name ?? (org?.name ?? "") },
                { k: "domain", label: "Domain", val: orgForm.domain ?? (org?.domain ?? "") },
              ].map(({ k, label, val }) => (
                <div key={k}>
                  <label className="mb-1 block text-xs text-slate-400">{label}</label>
                  <input value={val} onChange={(e) => setOrgForm((f) => ({ ...f, [k]: e.target.value }))}
                    className="w-full rounded-xl border border-border/60 bg-slate-800 px-3 py-2 text-sm text-foreground" />
                </div>
              ))}
              <Button onClick={() => orgMutation.mutate()} disabled={orgMutation.isPending}>
                {orgMutation.isPending ? "Saving…" : "Save Organization"}
              </Button>
            </>
          )}
        </div>
      )}

      {tab === "apikeys" && (
        <div className="max-w-lg space-y-3">
          {loadKeys ? <Skeleton className="h-32 w-full" /> : (
            (keys ?? []).map((k: any) => (
              <div key={k.id} className="flex items-center justify-between rounded-xl border border-border/40 p-3">
                <div>
                  <p className="text-sm font-medium text-foreground">{k.name}</p>
                  <p className="text-xs text-slate-500 font-mono">{k.keyPrefix}••••••••</p>
                </div>
                <span className={`text-xs ${k.active ? "text-emerald-400" : "text-slate-500"}`}>
                  {k.active ? "Active" : "Inactive"}
                </span>
              </div>
            ))
          )}
        </div>
      )}
    </PageShell>
  );
}

