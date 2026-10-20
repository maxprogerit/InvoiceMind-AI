"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Trash2, UserPlus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { PageHeader } from "@/components/layout/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input, Label } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/misc";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TBody, TD, TH, THead, TR } from "@/components/ui/table";
import { ApiError } from "@/lib/api/client";
import { useAddFirmMember, useFirmTeam, useRemoveFirmMember, useSetAccess } from "@/lib/api/hooks";
import type { ClientRef, FirmMember, Role } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/store";

const NONE = "NONE";
const ROLES: { id: Role; label: string }[] = [
  { id: "USER", label: "User" },
  { id: "APPROVER", label: "Approver" },
  { id: "ADMIN", label: "Admin" },
];

const schema = z.object({
  fullName: z.string().trim().min(1, "Required").max(200),
  email: z.string().email("Enter a valid e-mail"),
  password: z.string().min(10, "At least 10 characters").regex(/[A-Za-z]/, "Include a letter").regex(/\d/, "Include a digit"),
  firmRole: z.enum(["ACCOUNTANT", "FIRM_ADMIN"]),
  homeClient: z.string().min(1, "Choose a client"),
  role: z.enum(["USER", "APPROVER", "ADMIN"]),
});

function AddMember({ clients, open, onOpenChange }: { clients: ClientRef[]; open: boolean; onOpenChange: (o: boolean) => void }) {
  const add = useAddFirmMember();
  const active = clients.filter((c) => c.active);
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: "", email: "", password: "", firmRole: "ACCOUNTANT", homeClient: "", role: "APPROVER" },
  });
  const e = form.formState.errors;

  const submit = form.handleSubmit((v) =>
    add.mutate(
      { email: v.email, fullName: v.fullName, password: v.password, firmRole: v.firmRole, access: [{ organizationId: v.homeClient, role: v.role }] },
      {
        onSuccess: () => {
          toast.success(`${v.fullName} was added`);
          form.reset();
          onOpenChange(false);
        },
        onError: (err) => {
          if (err instanceof ApiError && err.code === "email_taken") form.setError("email", { message: "This e-mail is already registered" });
          else toast.error(err.message);
        },
      },
    ),
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add an accountant</DialogTitle>
          <DialogDescription>
            They start with one client, which is also where they land on sign-in. Give them more clients in the table afterwards.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <div>
            <Label htmlFor="f-name">Full name</Label>
            <Input id="f-name" aria-invalid={!!e.fullName} {...form.register("fullName")} />
            {e.fullName && <p className="mt-1 text-xs text-bad">{e.fullName.message}</p>}
          </div>
          <div>
            <Label htmlFor="f-email">E-mail</Label>
            <Input id="f-email" type="email" aria-invalid={!!e.email} {...form.register("email")} />
            {e.email && <p className="mt-1 text-xs text-bad">{e.email.message}</p>}
          </div>
          <div>
            <Label htmlFor="f-pass">Temporary password</Label>
            <Input id="f-pass" type="text" autoComplete="off" aria-invalid={!!e.password} {...form.register("password")} />
            {e.password && <p className="mt-1 text-xs text-bad">{e.password.message}</p>}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="f-client">First client</Label>
              <Select value={form.watch("homeClient")} onValueChange={(v) => form.setValue("homeClient", v, { shouldValidate: true })}>
                <SelectTrigger id="f-client" aria-invalid={!!e.homeClient}>
                  <SelectValue placeholder="Choose…" />
                </SelectTrigger>
                <SelectContent>
                  {active.map((c) => (
                    <SelectItem key={c.id} value={c.id}>
                      {c.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {e.homeClient && <p className="mt-1 text-xs text-bad">{e.homeClient.message}</p>}
            </div>
            <div>
              <Label htmlFor="f-role">Role there</Label>
              <Select value={form.watch("role")} onValueChange={(v) => form.setValue("role", v as Role)}>
                <SelectTrigger id="f-role">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ROLES.map((r) => (
                    <SelectItem key={r.id} value={r.id}>
                      {r.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <div>
            <Label htmlFor="f-firmrole">In the firm</Label>
            <Select value={form.watch("firmRole")} onValueChange={(v) => form.setValue("firmRole", v as "ACCOUNTANT" | "FIRM_ADMIN")}>
              <SelectTrigger id="f-firmrole">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ACCOUNTANT">Accountant: only the clients given below</SelectItem>
                <SelectItem value="FIRM_ADMIN">Firm admin: every client and the firm team</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={add.isPending}>
              {add.isPending && <Loader2 className="animate-spin" />} Add accountant
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export default function FirmTeamPage() {
  const router = useRouter();
  const me = useAuth((s) => s.user);
  const admin = me?.firmRole === "FIRM_ADMIN";
  useEffect(() => {
    if (me && !admin) router.replace("/invoices");
  }, [me, admin, router]);

  const { data, isLoading, error } = useFirmTeam();
  const setAccess = useSetAccess();
  const remove = useRemoveFirmMember();
  const [adding, setAdding] = useState(false);
  if (!admin) return null;

  const clients = data?.clients ?? [];
  const change = (m: FirmMember, c: ClientRef, value: string) =>
    setAccess.mutate(
      { userId: m.userId, organizationId: c.id, role: value === NONE ? null : (value as Role) },
      { onSuccess: () => toast.success("Saved"), onError: (err) => toast.error(err.message) },
    );

  return (
    <>
      <PageHeader
        title="Firm team"
        subtitle="Which accountant may work in which client, and with which role there."
        actions={
          <Button onClick={() => setAdding(true)} disabled={clients.every((c) => !c.active)}>
            <UserPlus /> Add accountant
          </Button>
        }
      />
      <Card className="overflow-hidden">
        {error ? (
          <p className="p-10 text-center text-sm text-bad">{error.message}</p>
        ) : (
          <Table aria-label="Access matrix">
            <THead>
              <TR>
                <TH>Person</TH>
                {clients.map((c) => (
                  <TH key={c.id}>
                    {c.name}
                    {!c.active && <span className="ml-1.5 text-dim">(inactive)</span>}
                  </TH>
                ))}
                <TH className="w-12" />
              </TR>
            </THead>
            <TBody>
              {isLoading &&
                Array.from({ length: 3 }).map((_, i) => (
                  <TR key={i}>
                    <TD colSpan={clients.length + 2}>
                      <Skeleton className="h-6" />
                    </TD>
                  </TR>
                ))}
              {data?.members.map((m) => {
                const self = m.userId === me?.id;
                return (
                  <TR key={m.userId}>
                    <TD>
                      <div className="flex items-center gap-2 font-medium">
                        {m.fullName}
                        {self && <Badge tone="accent">you</Badge>}
                        {m.firmRole === "FIRM_ADMIN" && <Badge tone="violet">firm admin</Badge>}
                        {!m.enabled && <Badge tone="bad">disabled</Badge>}
                      </div>
                      <div className="font-mono text-xs text-dim">{m.email}</div>
                    </TD>
                    {clients.map((c) => {
                      const role = m.access.find((a) => a.organizationId === c.id)?.role;
                      const home = m.homeOrganizationId === c.id;
                      // a firm admin reaches every client as ADMIN anyway; the table shows explicit grants only
                      return (
                        <TD key={c.id}>
                          <Select value={role ?? NONE} disabled={setAccess.isPending || (!c.active && !role)} onValueChange={(v) => change(m, c, v)}>
                            <SelectTrigger className="h-9 w-32" aria-label={`Role of ${m.fullName} in ${c.name}`}>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value={NONE} disabled={home}>
                                {home ? "— (home)" : "No access"}
                              </SelectItem>
                              {ROLES.map((r) => (
                                <SelectItem key={r.id} value={r.id}>
                                  {r.label}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </TD>
                      );
                    })}
                    <TD>
                      {!self && (
                        <Button
                          size="sm"
                          variant="ghost"
                          aria-label={`Remove ${m.fullName} from the firm`}
                          title="Remove from the firm (disables the account)"
                          disabled={remove.isPending}
                          onClick={() => {
                            if (window.confirm(`Remove ${m.fullName} from the firm? Their account is disabled and they lose every client.`)) {
                              remove.mutate(m.userId, { onSuccess: () => toast.success("Removed"), onError: (err) => toast.error(err.message) });
                            }
                          }}
                        >
                          <Trash2 />
                        </Button>
                      )}
                    </TD>
                  </TR>
                );
              })}
            </TBody>
          </Table>
        )}
      </Card>
      <p className="mt-4 max-w-3xl text-xs text-dim">
        A person&apos;s first client is their home: they land there when signing in and it cannot be taken away here. Access changes
        apply to new sessions at once; a session that is already open keeps its access for up to 15 minutes.
      </p>
      <AddMember clients={clients} open={adding} onOpenChange={setAdding} />
    </>
  );
}
