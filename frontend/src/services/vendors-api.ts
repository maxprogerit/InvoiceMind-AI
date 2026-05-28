import { apiGet, apiPost, apiPut, apiDelete } from "./api";

export interface Vendor {
  id: number;
  name: string;
  contactEmail: string | null;
  paymentTerms: string | null;
  riskScore: number;
  country: string | null;
  contactPhone: string | null;
  address: string | null;
}

export interface VendorStats {
  totalSpend: number;
  invoiceCount: number;
}

export const vendorsApi = {
  list: () => apiGet<Vendor[]>("/vendors"),
  get: (id: number) => apiGet<Vendor>(`/vendors/${id}`),
  create: (data: Partial<Vendor>) => apiPost<Vendor>("/vendors", data),
  update: (id: number, data: Partial<Vendor>) => apiPut<Vendor>(`/vendors/${id}`, data),
  delete: (id: number) => apiDelete(`/vendors/${id}`),
  stats: (id: number) => apiGet<VendorStats>(`/vendors/${id}/stats`),
};
