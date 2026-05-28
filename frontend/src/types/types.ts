export type EntityStatus = "active" | "inactive";
export type DealStage = "lead" | "qualified" | "proposal" | "won" | "lost";
export type InvoiceStatus = "draft" | "sent" | "paid" | "overdue";
export type TeamRole = "Admin" | "Sales" | "Finance" | "Support";
export type ToastType = "success" | "error" | "info";

export interface User {
  id: string;
  name: string;
  email: string;
  password: string;
}

export interface Client {
  id: string;
  name: string;
  email: string;
  phone: string;
  status: EntityStatus;
}

export interface Deal {
  id: string;
  title: string;
  clientId: string;
  value: number;
  stage: DealStage;
  closeDate: string;
}

export interface Contact {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  companyId: string;
}

export interface Company {
  id: string;
  name: string;
  industry: string;
  size: string;
  website: string;
}

export interface CalendarEvent {
  id: string;
  title: string;
  date: string;
  type: string;
  notes: string;
}

export interface DocumentItem {
  id: string;
  name: string;
  type: string;
  size: number;
  uploadedAt: string;
  linkedTo: string;
}

export interface Invoice {
  id: string;
  clientId: string;
  amount: number;
  dueDate: string;
  issuedDate: string;
  status: InvoiceStatus;
}

export interface Receipt {
  id: string;
  invoiceId: string;
  amount: number;
  date: string;
  method: string;
}

export interface TeamMember {
  id: string;
  name: string;
  email: string;
  role: TeamRole;
  status: EntityStatus;
}

export interface AppSettings {
  companyName: string;
  timezone: string;
  currency: string;
  theme: "light" | "dark";
  notifications: boolean;
}

export interface Toast {
  id: string;
  type: ToastType;
  message: string;
}

export interface ChartPoint {
  label: string;
  value: number;
}
