"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { FileText, Loader2, Plus, Search, Trash2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { AnswerView } from "@/components/knowledge/answer-view";
import { PageHeader } from "@/components/layout/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardLabel } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input, Label, Textarea } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/misc";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ApiError } from "@/lib/api/client";
import { useAddKnowledgeText, useAsk, useDeleteKnowledge, useKnowledgeDocuments, useUploadKnowledge } from "@/lib/api/hooks";
import { isReviewer } from "@/lib/api/types";
import { useAuth } from "@/lib/auth/store";
import { formatDate, formatNumber } from "@/lib/format";

const textSchema = z.object({
  title: z.string().trim().min(1, "Give the document a title").max(300),
  content: z.string().trim().min(20, "At least 20 characters").max(200_000, "Too long (200,000 characters max)"),
});

function AddDocument({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const addText = useAddKnowledgeText();
  const upload = useUploadKnowledge();
  const fileRef = useRef<HTMLInputElement>(null);
  const [fileTitle, setFileTitle] = useState("");
  const form = useForm<z.infer<typeof textSchema>>({ resolver: zodResolver(textSchema), defaultValues: { title: "", content: "" } });
  const busy = addText.isPending || upload.isPending;

  const done = (title: string) => {
    toast.success(`"${title}" was added to the knowledge base`);
    form.reset();
    setFileTitle("");
    onOpenChange(false);
  };
  const failed = (e: Error) => toast.error(e instanceof ApiError ? e.message : "Could not add the document");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>Add a rules document</DialogTitle>
          <DialogDescription>
            Accounting rules, approval limits, vendor agreements. It is split into passages and embedded so it can answer questions with sources.
          </DialogDescription>
        </DialogHeader>
        <Tabs defaultValue="text">
          <TabsList>
            <TabsTrigger value="text">Paste text</TabsTrigger>
            <TabsTrigger value="file">Upload file</TabsTrigger>
          </TabsList>
          <TabsContent value="text">
            <form
              className="space-y-4"
              noValidate
              onSubmit={form.handleSubmit((v) => addText.mutate(v, { onSuccess: () => done(v.title), onError: failed }))}
            >
              <div>
                <Label htmlFor="k-title">Title</Label>
                <Input id="k-title" placeholder="Approval limits 2026" aria-invalid={!!form.formState.errors.title} {...form.register("title")} />
                {form.formState.errors.title && <p className="mt-1 text-xs text-bad">{form.formState.errors.title.message}</p>}
              </div>
              <div>
                <Label htmlFor="k-content">Content</Label>
                <Textarea id="k-content" className="min-h-40" aria-invalid={!!form.formState.errors.content} {...form.register("content")} />
                {form.formState.errors.content && <p className="mt-1 text-xs text-bad">{form.formState.errors.content.message}</p>}
              </div>
              <DialogFooter>
                <Button type="submit" disabled={busy}>
                  {addText.isPending && <Loader2 className="animate-spin" />} Add document
                </Button>
              </DialogFooter>
            </form>
          </TabsContent>
          <TabsContent value="file">
            <div className="space-y-4">
              <div>
                <Label htmlFor="k-file-title">Title (optional)</Label>
                <Input id="k-file-title" placeholder="Defaults to the file name" value={fileTitle} onChange={(e) => setFileTitle(e.target.value)} />
              </div>
              <div>
                <Label htmlFor="k-file">File</Label>
                <input
                  ref={fileRef}
                  id="k-file"
                  type="file"
                  accept=".pdf,.txt,.md,text/plain,text/markdown,application/pdf"
                  className="block w-full rounded-control border border-line bg-bg/60 p-2 text-sm text-muted file:mr-3 file:rounded-md file:border-0 file:bg-surface-3 file:px-3 file:py-1.5 file:font-mono file:text-xs file:text-fg"
                />
                <p className="mt-1.5 text-xs text-dim">PDF (scans are OCR-ed), plain text or Markdown.</p>
              </div>
              <DialogFooter>
                <Button
                  disabled={busy}
                  onClick={() => {
                    const file = fileRef.current?.files?.[0];
                    if (!file) return toast.error("Choose a file first");
                    upload.mutate({ file, title: fileTitle.trim() || undefined }, { onSuccess: (d) => done(d.title), onError: failed });
                  }}
                >
                  {upload.isPending && <Loader2 className="animate-spin" />} Upload
                </Button>
              </DialogFooter>
            </div>
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}

export default function KnowledgePage() {
  const router = useRouter();
  const user = useAuth((s) => s.user);
  const reviewer = isReviewer(user?.role);
  const admin = user?.role === "ADMIN";
  useEffect(() => {
    if (user && !reviewer) router.replace("/invoices");
  }, [user, reviewer, router]);

  const docs = useKnowledgeDocuments(reviewer);
  const remove = useDeleteKnowledge();
  const ask = useAsk();
  const [adding, setAdding] = useState(false);
  const [question, setQuestion] = useState("");
  if (!reviewer) return null;

  return (
    <>
      <PageHeader
        title="Knowledge"
        subtitle="Your rules and policies. They explain why invoices get flagged, with sources."
        actions={
          admin ? (
            <Button onClick={() => setAdding(true)}>
              <Plus /> Add document
            </Button>
          ) : undefined
        }
      />

      <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <Card className="p-6">
          <CardLabel>Documents</CardLabel>
          {docs.isLoading && <Skeleton className="mt-4 h-24" />}
          {docs.error && <p className="mt-4 text-sm text-bad">{docs.error.message}</p>}
          {docs.data?.length === 0 && (
            <div className="mt-6 flex flex-col items-center gap-3 py-10 text-center text-sm text-dim">
              <FileText className="size-6" />
              <p className="max-w-xs">
                No documents yet.{admin ? " Add your approval limits and accounting rules to enable sourced explanations." : " Ask an admin to add your policies."}
              </p>
            </div>
          )}
          <ul className="mt-4 space-y-2.5">
            {docs.data?.map((d) => (
              <li key={d.id} className="flex items-center gap-3 rounded-control border border-line bg-bg/40 p-3.5">
                <div className="grid size-9 shrink-0 place-items-center rounded-md bg-surface-2 text-muted">
                  <FileText className="size-4" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{d.title}</p>
                  <p className="font-mono text-[11px] text-dim">
                    {d.chunkCount} passages · {formatNumber(d.charCount)} chars · {d.createdByName} · {formatDate(d.createdAt)}
                  </p>
                </div>
                {admin && (
                  <button
                    aria-label={`Delete ${d.title}`}
                    className="rounded-md p-2 text-dim transition-colors hover:bg-bad/10 hover:text-bad"
                    onClick={() => {
                      if (confirm(`Remove "${d.title}" from the knowledge base?`)) {
                        remove.mutate(d.id, { onSuccess: () => toast.success("Document removed"), onError: (e) => toast.error(e.message) });
                      }
                    }}
                  >
                    <Trash2 className="size-4" />
                  </button>
                )}
              </li>
            ))}
          </ul>
        </Card>

        <Card className="p-6">
          <CardLabel>Ask your rules</CardLabel>
          <form
            className="mt-4 flex gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              if (question.trim()) ask.mutate(question.trim(), { onError: (err) => toast.error(err.message) });
            }}
          >
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-dim" aria-hidden />
              <Input
                aria-label="Question"
                className="pl-9"
                placeholder="e.g. What is the approval limit for one invoice?"
                maxLength={1000}
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
              />
            </div>
            <Button type="submit" disabled={ask.isPending || !question.trim()}>
              {ask.isPending && <Loader2 className="animate-spin" />} Ask
            </Button>
          </form>
          <div className="mt-6">
            {ask.data ? (
              <AnswerView answer={ask.data} />
            ) : (
              <p className="text-sm text-dim">Answers use only the documents on the left and always show which passages they came from.</p>
            )}
          </div>
        </Card>
      </div>

      <AddDocument open={adding} onOpenChange={setAdding} />
    </>
  );
}
