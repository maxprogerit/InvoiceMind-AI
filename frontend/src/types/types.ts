export type EntityStatus = "active" | "inactive";
export type DealStage = "New" | "Qualified" | "Proposal" | "Negotiation" | "Won" | "Lost";
export type InvoiceStatus = "Draft" | "Pending" | "Paid" | "Overdue";
export type TeamRole = "Admin" | "Sales" | "Finance" | "Support";
export type ToastType = "success" | "error" | "info";
export type DocumentCategory = "Contract" | "Proposal" | "Invoice" | "Receipt" | "Report" | "Legal" | "Client File";
export type ReceiptCategory = "Travel" | "Software" | "Office" | "Marketing" | "Operations" | "Other";
export type BillingPlan = "Starter" | "Pro" | "Enterprise";

export interface User {
  id: string;
  name: string;
  email: string;
  password: string;
  createdAt: string;
}

export interface Client {
  id: string;
  name: string;
  email: string;
  phone: string;
  company: string;
  industry: string;
  status: EntityStatus;
  estimatedValue: number;
  notes: string;
  tags: string[];
  lastContactDate: string;
}

export interface Deal {
  id: string;
  title: string;
  clientId: string;
  company: string;
  assignedTeamMemberId: string;
  value: number;
  stage: DealStage;
  probability: number;
  expectedCloseDate: string;
  notes: string;
}

export interface Contact {
  id: string;
  name: string;
  email: string;
  phone: string;
  companyId: string;
  role: string;
  linkedClientId: string;
  communicationHistory: string[];
  notes: string;
  lastCommunication: string;
}

export interface Company {
  id: string;
  name: string;
  industry: string;
  revenue: number;
  status: EntityStatus;
  notes: string;
  size: string;
  website: string;
}

export interface CalendarEvent {
  id: string;
  title: string;
  type: string;
  date: string;
  time: string;
  linkedClientId: string;
  linkedDealId: string;
  description: string;
}

export interface DocumentItem {
  id: string;
  type: DocumentCategory;
  fileName: string;
  fileSize: number;
  uploadDate: string;
  linkedClientId: string;
  linkedCompanyId: string;
  content?: string;
}

export interface InvoiceItem {
  id: string;
  description: string;
  quantity: number;
  price: number;
}

export interface Invoice {
  id: string;
  invoiceNumber: string;
  clientId: string;
  items: InvoiceItem[];
  subtotal: number;
  tax: number;
  total: number;
  dueDate: string;
  issuedDate: string;
  status: InvoiceStatus;
  notes: string;
}

export interface Receipt {
  id: string;
  receiptNumber: string;
  vendor: string;
  amount: number;
  category: ReceiptCategory;
  date: string;
  paymentMethod: string;
  linkedClientId: string;
  linkedDealId: string;
  attachment?: string;
  notes: string;
}

export interface TeamMember {
  id: string;
  name: string;
  email: string;
  role: TeamRole;
  department: string;
  status: EntityStatus;
  assignedDeals: number;
  tasksCompleted: number;
  revenueGenerated: number;
}

export interface AppSettings {
  profileName: string;
  companyName: string;
  workspaceLabel: string;
  billingPlan: BillingPlan;
  timezone: string;
  currency: string;
  theme: "light" | "dark";
  notifications: {
    deals: boolean;
    invoices: boolean;
    reminders: boolean;
  };
  security: {
    twoFactor: boolean;
  };
  apiIntegrations: {
    slackWebhook: string;
  };
}

export interface Toast {
  id: string;
  type: ToastType;
  message: string;
}

export interface TaskItem {
  id: string;
  title: string;
  dueDate: string;
  status: "todo" | "in_progress" | "done";
  assignedTeamMemberId: string;
  linkedClientId: string;
}

export interface NotificationItem {
  id: string;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface AssistantMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  createdAt: string;
}

export interface ActivityItem {
  id: string;
  entity: "client" | "deal" | "contact" | "company" | "invoice" | "receipt" | "document" | "event" | "task";
  entityId: string;
  text: string;
  createdAt: string;
}

export interface WorkspaceData {
  clients: Client[];
  deals: Deal[];
  contacts: Contact[];
  companies: Company[];
  documents: DocumentItem[];
  invoices: Invoice[];
  receipts: Receipt[];
  calendarEvents: CalendarEvent[];
  tasks: TaskItem[];
  teamMembers: TeamMember[];
  settings: AppSettings;
  notifications: NotificationItem[];
  assistantMessages: AssistantMessage[];
  activity: ActivityItem[];
}

export interface ChartPoint {
  label: string;
  value: number;
}
