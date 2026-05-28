import type {
  AppSettings,
  CalendarEvent,
  Client,
  Company,
  Contact,
  Deal,
  DocumentItem,
  Invoice,
  Receipt,
  TeamMember,
  User,
} from "../types/types";

export const users: User[] = [
  { id: "user-1", name: "Admin User", email: "admin@invoicemind.ai", password: "admin123" },
];

export const clients: Client[] = [
  { id: "client-1", name: "Apex Labs", email: "billing@apexlabs.com", phone: "+1 555 0001", status: "active" },
  { id: "client-2", name: "Northstar Retail", email: "finance@northstar.com", phone: "+1 555 0002", status: "active" },
];

export const companies: Company[] = [
  { id: "company-1", name: "Apex Labs", industry: "SaaS", size: "51-200", website: "https://apexlabs.com" },
  { id: "company-2", name: "Northstar Retail", industry: "Retail", size: "201-500", website: "https://northstar.com" },
];

export const contacts: Contact[] = [
  {
    id: "contact-1",
    firstName: "Nora",
    lastName: "Reyes",
    email: "nora@apexlabs.com",
    phone: "+1 555 1010",
    companyId: "company-1",
  },
  {
    id: "contact-2",
    firstName: "Leo",
    lastName: "Trent",
    email: "leo@northstar.com",
    phone: "+1 555 2020",
    companyId: "company-2",
  },
];

export const deals: Deal[] = [
  { id: "deal-1", title: "Apex Annual Contract", clientId: "client-1", value: 28000, stage: "proposal", closeDate: "2026-06-30" },
  { id: "deal-2", title: "Northstar Expansion", clientId: "client-2", value: 42000, stage: "qualified", closeDate: "2026-07-15" },
];

export const calendarEvents: CalendarEvent[] = [
  { id: "event-1", title: "Client Kickoff", date: "2026-06-01", type: "meeting", notes: "Apex onboarding call" },
  { id: "event-2", title: "Invoice Review", date: "2026-06-03", type: "finance", notes: "Q2 cashflow review" },
];

export const documents: DocumentItem[] = [
  { id: "doc-1", name: "Apex-MSA.pdf", type: "contract", size: 245000, uploadedAt: "2026-05-20", linkedTo: "Apex Labs" },
  { id: "doc-2", name: "Northstar-NDA.pdf", type: "legal", size: 162000, uploadedAt: "2026-05-21", linkedTo: "Northstar Retail" },
];

export const invoices: Invoice[] = [
  { id: "INV-1001", clientId: "client-1", amount: 8500, dueDate: "2026-06-10", issuedDate: "2026-05-20", status: "sent" },
  { id: "INV-1002", clientId: "client-2", amount: 12600, dueDate: "2026-06-18", issuedDate: "2026-05-22", status: "draft" },
];

export const receipts: Receipt[] = [
  { id: "REC-2001", invoiceId: "INV-1001", amount: 8500, date: "2026-05-26", method: "Bank Transfer" },
];

export const teamMembers: TeamMember[] = [
  { id: "team-1", name: "Maya Cole", email: "maya@invoicemind.ai", role: "Sales", status: "active" },
  { id: "team-2", name: "Ivan Ross", email: "ivan@invoicemind.ai", role: "Finance", status: "active" },
];

export const settings: AppSettings = {
  companyName: "InvoiceMind AI",
  timezone: "UTC",
  currency: "USD",
  theme: "light",
  notifications: true,
};
