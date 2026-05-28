import { apiGet, apiPost, apiPut, apiPostForm, apiDownload } from "./api";

export interface Invoice {
  id: number;
  invoiceNumber: string;
  vendorName: string;
  totalAmount: number;
  currency: string;
  dueDate: string | null;
  status: string;
  confidenceScore: number | null;
  smartCategory: string | null;
}

export interface InvoiceStats {
  total: number;
  totalAmount: number;
  pending: number;
  approved: number;
}

export const invoicesApi = {
  list: () => apiGet<Invoice[]>("/invoices"),
  get: (id: number) => apiGet<Invoice>(`/invoices/${id}`),
  updateStatus: (id: number, status: string) =>
    apiPut<{ id: number; status: string }>(`/invoices/${id}/status`, { status }),
  stats: () => apiGet<InvoiceStats>("/invoices/stats"),
  export: () => apiDownload("/invoices/export/csv", "invoices.csv"),
  upload: (file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    return apiPostForm<{ documentId: number; invoiceId: number }>("/invoices/upload", fd);
  },
};
