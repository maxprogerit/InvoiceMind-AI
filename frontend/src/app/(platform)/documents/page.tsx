"use client";

import { useState, useCallback } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { PageShell } from "@/components/platform/page-shell";
import { Button } from "@/components/ui/button";
import { SkeletonRow } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Drawer } from "@/components/ui/drawer";
import { ConfirmModal } from "@/components/ui/modal";
import { statusBadge } from "@/components/ui/badge";
import { useNotificationStore } from "@/store/notification-store";
import { documentsApi, Document } from "@/services/documents-api";
import { Upload, RefreshCw, Trash2, Eye } from "lucide-react";

export default function DocumentsPage() {
  const qc = useQueryClient();
  const { success, error } = useNotificationStore();
  const [dragging, setDragging] = useState(false);
  const [selectedDoc, setSelectedDoc] = useState<Document | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<number | null>(null);

  const { data: docs = [], isLoading } = useQuery({
    queryKey: ["documents"],
    queryFn: documentsApi.list,
    refetchInterval: 10000,
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => documentsApi.upload(file),
    onSuccess: (result) => {
      success(`Document uploaded — processing started (trace: ${result.traceId})`);
      qc.invalidateQueries({ queryKey: ["documents"] });
      qc.invalidateQueries({ queryKey: ["dashboard-overview"] });
      qc.invalidateQueries({ queryKey: ["executions"] });
    },
    onError: (e: Error) => error(`Upload failed: ${e.message}`),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => documentsApi.delete(id),
    onSuccess: () => {
      success("Document deleted");
      qc.invalidateQueries({ queryKey: ["documents"] });
    },
    onError: (e: Error) => error(`Delete failed: ${e.message}`),
  });

  const retryMutation = useMutation({
    mutationFn: (id: number) => documentsApi.retry(id),
    onSuccess: () => {
      success("Retry started");
      qc.invalidateQueries({ queryKey: ["documents"] });
    },
    onError: (e: Error) => error(`Retry failed: ${e.message}`),
  });

  const handleFiles = useCallback((files: FileList | null) => {
    if (!files) return;
    Array.from(files).forEach((f) => uploadMutation.mutate(f));
  }, [uploadMutation]);

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    handleFiles(e.dataTransfer.files);
  }, [handleFiles]);

  return (
    <PageShell title="Documents" subtitle="Manage invoices, receipts, contracts, and business files.">
      {/* Upload zone */}
      <div
        onDrop={onDrop}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        className={`rounded-xl border-2 border-dashed p-10 text-center transition-colors ${
          dragging ? "border-cyan-400 bg-cyan-500/5" : "border-cyan-300/40 hover:border-cyan-300/60"
        }`}
      >
        <Upload className="mx-auto mb-3 h-8 w-8 text-slate-500" />
        <p className="text-sm text-slate-400">Drop files here or{" "}
          <label className="cursor-pointer text-cyan-300 hover:text-cyan-200">
            browse
            <input type="file" multiple accept=".pdf,.png,.jpg,.docx" className="hidden"
              onChange={(e) => handleFiles(e.target.files)} />
          </label>
        </p>
        <p className="mt-1 text-xs text-slate-600">PDF, PNG, JPG, DOCX</p>
        {uploadMutation.isPending && (
          <p className="mt-2 text-xs text-cyan-400 animate-pulse">Uploading…</p>
        )}
      </div>

      {/* Document list */}
      <div className="mt-6 space-y-2">
        {isLoading ? (
          Array.from({ length: 5 }).map((_, i) => <SkeletonRow key={i} />)
        ) : docs.length === 0 ? (
          <EmptyState title="No documents yet" description="Upload your first file above to get started." />
        ) : (
          docs.map((doc) => (
            <div key={doc.id} className="flex items-center gap-4 rounded-xl border border-border/40 p-4">
              <div className="flex-1 min-w-0">
                <p className="truncate font-medium text-foreground">{doc.fileName}</p>
                <p className="text-xs text-slate-500">{doc.documentType ?? "document"} •{" "}
                  {new Date(doc.createdAt).toLocaleDateString()}
                  {doc.confidenceScore != null && ` • ${(doc.confidenceScore * 100).toFixed(1)}% confidence`}
                </p>
              </div>
              {statusBadge(doc.processingStatus)}
              <div className="flex gap-1">
                <button onClick={() => setSelectedDoc(doc)}
                  className="rounded p-1.5 text-slate-400 hover:bg-slate-800 hover:text-slate-200">
                  <Eye className="h-4 w-4" />
                </button>
                {(doc.processingStatus === "FAILED" || doc.processingStatus === "UPLOADED") && (
                  <button onClick={() => retryMutation.mutate(doc.id)}
                    className="rounded p-1.5 text-slate-400 hover:bg-slate-800 hover:text-cyan-300">
                    <RefreshCw className="h-4 w-4" />
                  </button>
                )}
                <button onClick={() => setDeleteTarget(doc.id)}
                  className="rounded p-1.5 text-slate-400 hover:bg-slate-800 hover:text-rose-400">
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Detail drawer */}
      <Drawer open={!!selectedDoc} onClose={() => setSelectedDoc(null)} title="Document Details">
        {selectedDoc && (
          <div className="space-y-4 text-sm">
            <div><span className="text-slate-500">File name:</span><p className="mt-0.5 text-foreground">{selectedDoc.fileName}</p></div>
            <div><span className="text-slate-500">Type:</span><p className="mt-0.5 text-foreground">{selectedDoc.fileType}</p></div>
            <div><span className="text-slate-500">Status:</span><p className="mt-0.5">{statusBadge(selectedDoc.processingStatus)}</p></div>
            <div><span className="text-slate-500">Document type:</span><p className="mt-0.5 text-foreground">{selectedDoc.documentType ?? "—"}</p></div>
            <div><span className="text-slate-500">OCR Confidence:</span><p className="mt-0.5 text-foreground">{selectedDoc.confidenceScore != null ? (selectedDoc.confidenceScore * 100).toFixed(1) + "%" : "—"}</p></div>
            <div><span className="text-slate-500">Uploaded:</span><p className="mt-0.5 text-foreground">{new Date(selectedDoc.createdAt).toLocaleString()}</p></div>
            <div><span className="text-slate-500">Storage path:</span><p className="mt-0.5 break-all text-xs text-slate-400">{selectedDoc.storagePath}</p></div>
            <div className="pt-2 flex gap-2">
              {(selectedDoc.processingStatus === "FAILED" || selectedDoc.processingStatus === "UPLOADED") && (
                <Button onClick={() => { retryMutation.mutate(selectedDoc.id); setSelectedDoc(null); }}>
                  <RefreshCw className="mr-2 h-4 w-4" /> Retry Processing
                </Button>
              )}
              <Button onClick={() => { setDeleteTarget(selectedDoc.id); setSelectedDoc(null); }}
                className="border-rose-400/40 bg-rose-500/15 text-rose-300 hover:bg-rose-500/20">
                <Trash2 className="mr-2 h-4 w-4" /> Delete
              </Button>
            </div>
          </div>
        )}
      </Drawer>

      {/* Delete confirm */}
      <ConfirmModal
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => { if (deleteTarget !== null) deleteMutation.mutate(deleteTarget); }}
        title="Delete Document"
        message="Are you sure you want to delete this document? This action cannot be undone."
        confirmLabel="Delete"
        danger
      />
    </PageShell>
  );
}

