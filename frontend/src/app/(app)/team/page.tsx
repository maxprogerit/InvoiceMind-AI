"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, UserPlus } from "lucide-react";
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
import { useCreateUser, useUpdateUser, useUsers } from "@/lib/api/hooks";
import type { Role } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/store";
import { formatDate } from "@/lib/format";

const ROLES: { id: Role; label: string; hint: string }[] = [
  { id: "USER", label: "User", hint: "Uploads invoices, sees only their own" },
  { id: "APPROVER", label: "Approver", hint: "Sees the whole organisation, approves and rejects" },
  { id: "ADMIN", label: "Admin", hint: "Everything, plus team management and deletion" },
];

const schema = z.object({
  fullName: z.string().trim().min(1, "Required").max(200),
  email: z.string().email("Enter a valid e-mail"),
  role: z.enum(["USER", "APPROVER", "ADMIN"]),
  password: z.string().min(10, "At least 10 characters").regex(/[A-Za-z]/, "Include a letter").regex(/\d/, "Include a digit"),
});

function AddMember({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const create = useCreateUser();
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { fullName: "", email: "", role: "USER", password: "" } });
  const e = form.formState.errors;
  const role = form.watch("role");

  const submit = form.handleSubmit((values) =>
    create.mutate(values, {
      onSuccess: () => {
        toast.success(`${values.fullName} was added`);
        form.reset();
        onOpenChange(false);
      },
      onError: (err) => {
        if (err instanceof ApiError && err.code === "email_taken") form.setError("email", { message: "This e-mail is already registered" });
        else toast.error(err.message);
      },
    }),
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a team member</DialogTitle>
          <DialogDescription>Share the temporary password with them directly; they can sign in right away.</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <div>
            <Label htmlFor="m-name">Full name</Label>
            <Input id="m-name" aria-invalid={!!e.fullName} {...form.register("fullName")} />
            {e.fullName && <p className="mt-1 text-xs text-bad">{e.fullName.message}</p>}
          </div>
          <div>
            <Label htmlFor="m-email">E-mail</Label>
            <Input id="m-email" type="email" aria-invalid={!!e.email} {...form.register("email")} />
            {e.email && <p className="mt-1 text-xs text-bad">{e.email.message}</p>}
          </div>
          <div>
            <Label htmlFor="m-role">Role</Label>
            <Select value={role} onValueChange={(v) => form.setValue("role", v as Role)}>
              <SelectTrigger id="m-role">
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
            <p className="mt-1.5 text-xs text-dim">{ROLES.find((r) => r.id === role)?.hint}</p>
          </div>
          <div>
            <Label htmlFor="m-pass">Temporary password</Label>
            <Input id="m-pass" type="text" autoComplete="off" aria-invalid={!!e.password} {...form.register("password")} />
            {e.password && <p className="mt-1 text-xs text-bad">{e.password.message}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={create.isPending}>
              {create.isPending && <Loader2 className="animate-spin" />} Add member
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export default function TeamPage() {
  const router = useRouter();
  const me = useAuth((s) => s.user);
  const admin = me?.role === "ADMIN";
  useEffect(() => {
    if (me && !admin) router.replace("/invoices");
  }, [me, admin, router]);

  const { data, isLoading, error } = useUsers();
  const update = useUpdateUser();
  const [adding, setAdding] = useState(false);
  if (!admin) return null;

  const change = (id: string, body: { role?: Role; enabled?: boolean }) =>
    update.mutate({ id, ...body }, { onSuccess: () => toast.success("Saved"), onError: (e) => toast.error(e.message) });

  return (
    <>
      <PageHeader
        title="Team"
        subtitle="Who can upload, approve and administer in your organisation."
        actions={
          <Button onClick={() => setAdding(true)}>
            <UserPlus /> Add member
          </Button>
        }
      />
      <Card className="overflow-hidden">
        {error ? (
          <p className="p-10 text-center text-sm text-bad">{error.message}</p>
        ) : (
          <Table>
            <THead>
              <TR>
                <TH>Member</TH>
                <TH>Role</TH>
                <TH>Status</TH>
                <TH>Joined</TH>
              </TR>
            </THead>
            <TBody>
              {isLoading && Array.from({ length: 3 }).map((_, i) => (
                <TR key={i}>
                  <TD colSpan={4}>
                    <Skeleton className="h-6" />
                  </TD>
                </TR>
              ))}
              {data?.map((u) => {
                const self = u.id === me?.id;
                return (
                  <TR key={u.id}>
                    <TD>
                      <div className="font-medium">
                        {u.fullName} {self && <Badge tone="accent">you</Badge>}
                      </div>
                      <div className="font-mono text-xs text-dim">{u.email}</div>
                    </TD>
                    <TD>
                      <Select value={u.role} disabled={self} onValueChange={(v) => change(u.id, { role: v as Role })}>
                        <SelectTrigger className="h-9 w-36" aria-label={`Role of ${u.fullName}`}>
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
                    </TD>
                    <TD>
                      {self ? (
                        <Badge tone="ok">Active</Badge>
                      ) : (
                        <Button size="sm" variant={u.enabled ? "outline" : "success"} onClick={() => change(u.id, { enabled: !u.enabled })}>
                          {u.enabled ? "Disable" : "Enable"}
                        </Button>
                      )}
                    </TD>
                    <TD className="font-mono text-xs text-muted">{formatDate(u.createdAt)}</TD>
                  </TR>
                );
              })}
            </TBody>
          </Table>
        )}
      </Card>
      <AddMember open={adding} onOpenChange={setAdding} />
    </>
  );
}
