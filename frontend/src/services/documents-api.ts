import { apiGet, apiPostForm, apiDelete } from "./api";

export interface Document {
  id: number;
  fileName: string;
  fileType: string;
  processingStatus: string;
  confidenceScore: number | null;
  documentType: string | null;
  createdAt: string;
  storagePath: string;
}

export interface UploadResult {
  documentId: number;
  status: string;
  storagePath: string;
  traceId: string;
}

export const documentsApi = {
  list: () => apiGet<Document[]>("/documents"),
  get: (id: number) => apiGet<Document>(`/documents/${id}`),
  upload: (file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    return apiPostForm<UploadResult>("/documents/upload", fd);
  },
  delete: (id: number) => apiDelete(`/documents/${id}`),
  retry: (id: number) => {
    const fd = new FormData();
    return fetch(
      (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1") + `/documents/${id}/retry`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${typeof window !== "undefined" ? localStorage.getItem("invoicemind-token") ?? "" : ""}`,
        },
      }
    ).then((r) => r.json() as Promise<Document>);
  },
};
