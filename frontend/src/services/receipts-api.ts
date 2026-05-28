import { apiGet, apiPostForm, apiDelete, apiDownload } from "./api";

export interface Receipt {
  id: number;
  merchantName: string;
  totalAmount: number;
  taxAmount: number;
  expenseCategory: string | null;
  confidenceScore: number | null;
  receiptDate: string | null;
  merchantCategory: string | null;
  status: string;
  createdAt: string;
}

export const receiptsApi = {
  list: () => apiGet<Receipt[]>("/receipts"),
  get: (id: number) => apiGet<Receipt>(`/receipts/${id}`),
  upload: (file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    return apiPostForm<Receipt>("/receipts/upload", fd);
  },
  delete: (id: number) => apiDelete(`/receipts/${id}`),
  export: () => apiDownload("/receipts/export/csv", "receipts.csv"),
};
