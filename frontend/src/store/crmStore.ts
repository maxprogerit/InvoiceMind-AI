import { create } from "zustand";
import {
  calendarEvents,
  clients,
  companies,
  contacts,
  deals,
  documents,
  invoices,
  receipts,
  settings,
  teamMembers,
  users,
} from "../data/mockData";
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
  Toast,
  ToastType,
  User,
} from "../types/types";
import { uid } from "../utils/helpers";

type AuthPayload = Pick<User, "email" | "password">;
type RegisterPayload = Pick<User, "name" | "email" | "password">;

interface CRMState {
  users: User[];
  currentUser: User | null;
  isAuthenticated: boolean;
  clients: Client[];
  deals: Deal[];
  contacts: Contact[];
  companies: Company[];
  events: CalendarEvent[];
  documents: DocumentItem[];
  invoices: Invoice[];
  receipts: Receipt[];
  team: TeamMember[];
  settings: AppSettings;
  toasts: Toast[];
  login: (payload: AuthPayload) => void;
  register: (payload: RegisterPayload) => void;
  logout: () => void;
  pushToast: (message: string, type?: ToastType) => void;
  removeToast: (id: string) => void;
  addClient: (payload: Omit<Client, "id">) => void;
  updateClient: (id: string, payload: Omit<Client, "id">) => void;
  deleteClient: (id: string) => void;
  addDeal: (payload: Omit<Deal, "id">) => void;
  updateDeal: (id: string, payload: Omit<Deal, "id">) => void;
  deleteDeal: (id: string) => void;
  addContact: (payload: Omit<Contact, "id">) => void;
  updateContact: (id: string, payload: Omit<Contact, "id">) => void;
  deleteContact: (id: string) => void;
  addCompany: (payload: Omit<Company, "id">) => void;
  updateCompany: (id: string, payload: Omit<Company, "id">) => void;
  deleteCompany: (id: string) => void;
  addEvent: (payload: Omit<CalendarEvent, "id">) => void;
  updateEvent: (id: string, payload: Omit<CalendarEvent, "id">) => void;
  deleteEvent: (id: string) => void;
  addDocument: (payload: Omit<DocumentItem, "id" | "uploadedAt">) => void;
  addDocumentFile: (file: File, linkedTo: string, type: string) => void;
  deleteDocument: (id: string) => void;
  addInvoice: (payload: Omit<Invoice, "id">) => void;
  updateInvoice: (id: string, payload: Omit<Invoice, "id">) => void;
  deleteInvoice: (id: string) => void;
  addReceipt: (payload: Omit<Receipt, "id">) => void;
  updateReceipt: (id: string, payload: Omit<Receipt, "id">) => void;
  deleteReceipt: (id: string) => void;
  addTeamMember: (payload: Omit<TeamMember, "id">) => void;
  updateTeamMember: (id: string, payload: Omit<TeamMember, "id">) => void;
  deleteTeamMember: (id: string) => void;
  updateSettings: (payload: AppSettings) => void;
}

function mutateItem<T extends { id: string }>(items: T[], id: string, payload: Omit<T, "id">): T[] {
  return items.map((item) => (item.id === id ? { id, ...payload } as T : item));
}

export const useCRMStore = create<CRMState>((set, get) => ({
  users,
  currentUser: null,
  isAuthenticated: false,
  clients,
  deals,
  contacts,
  companies,
  events: calendarEvents,
  documents,
  invoices,
  receipts,
  team: teamMembers,
  settings,
  toasts: [],

  login: ({ email, password }) => {
    const match = get().users.find((user) => user.email === email && user.password === password);
    if (!match) {
      throw new Error("Invalid email or password.");
    }
    set({ currentUser: match, isAuthenticated: true });
  },

  register: ({ name, email, password }) => {
    const exists = get().users.some((user) => user.email === email);
    if (exists) {
      throw new Error("Email already exists.");
    }
    const user: User = { id: uid("user"), name, email, password };
    set((state) => ({
      users: [...state.users, user],
      currentUser: user,
      isAuthenticated: true,
    }));
  },

  logout: () => set({ currentUser: null, isAuthenticated: false }),

  pushToast: (message, type = "info") =>
    set((state) => ({
      toasts: [...state.toasts, { id: uid("toast"), message, type }],
    })),

  removeToast: (id) =>
    set((state) => ({ toasts: state.toasts.filter((toast) => toast.id !== id) })),

  addClient: (payload) =>
    set((state) => ({ clients: [...state.clients, { id: uid("client"), ...payload }] })),
  updateClient: (id, payload) =>
    set((state) => ({ clients: mutateItem(state.clients, id, payload) })),
  deleteClient: (id) =>
    set((state) => ({ clients: state.clients.filter((client) => client.id !== id) })),

  addDeal: (payload) =>
    set((state) => ({ deals: [...state.deals, { id: uid("deal"), ...payload }] })),
  updateDeal: (id, payload) =>
    set((state) => ({ deals: mutateItem(state.deals, id, payload) })),
  deleteDeal: (id) =>
    set((state) => ({ deals: state.deals.filter((deal) => deal.id !== id) })),

  addContact: (payload) =>
    set((state) => ({ contacts: [...state.contacts, { id: uid("contact"), ...payload }] })),
  updateContact: (id, payload) =>
    set((state) => ({ contacts: mutateItem(state.contacts, id, payload) })),
  deleteContact: (id) =>
    set((state) => ({ contacts: state.contacts.filter((contact) => contact.id !== id) })),

  addCompany: (payload) =>
    set((state) => ({ companies: [...state.companies, { id: uid("company"), ...payload }] })),
  updateCompany: (id, payload) =>
    set((state) => ({ companies: mutateItem(state.companies, id, payload) })),
  deleteCompany: (id) =>
    set((state) => ({ companies: state.companies.filter((company) => company.id !== id) })),

  addEvent: (payload) =>
    set((state) => ({ events: [...state.events, { id: uid("event"), ...payload }] })),
  updateEvent: (id, payload) =>
    set((state) => ({ events: mutateItem(state.events, id, payload) })),
  deleteEvent: (id) =>
    set((state) => ({ events: state.events.filter((event) => event.id !== id) })),

  addDocument: (payload) =>
    set((state) => ({
      documents: [
        ...state.documents,
        {
          id: uid("doc"),
          uploadedAt: new Date().toISOString().slice(0, 10),
          ...payload,
        },
      ],
    })),
  addDocumentFile: (file, linkedTo, type) =>
    set((state) => ({
      documents: [
        ...state.documents,
        {
          id: uid("doc"),
          name: file.name,
          size: file.size,
          linkedTo,
          type,
          uploadedAt: new Date().toISOString().slice(0, 10),
        },
      ],
    })),
  deleteDocument: (id) =>
    set((state) => ({ documents: state.documents.filter((doc) => doc.id !== id) })),

  addInvoice: (payload) =>
    set((state) => ({ invoices: [...state.invoices, { id: uid("INV"), ...payload }] })),
  updateInvoice: (id, payload) =>
    set((state) => ({ invoices: mutateItem(state.invoices, id, payload) })),
  deleteInvoice: (id) =>
    set((state) => ({ invoices: state.invoices.filter((invoice) => invoice.id !== id) })),

  addReceipt: (payload) =>
    set((state) => ({ receipts: [...state.receipts, { id: uid("REC"), ...payload }] })),
  updateReceipt: (id, payload) =>
    set((state) => ({ receipts: mutateItem(state.receipts, id, payload) })),
  deleteReceipt: (id) =>
    set((state) => ({ receipts: state.receipts.filter((receipt) => receipt.id !== id) })),

  addTeamMember: (payload) =>
    set((state) => ({ team: [...state.team, { id: uid("team"), ...payload }] })),
  updateTeamMember: (id, payload) =>
    set((state) => ({ team: mutateItem(state.team, id, payload) })),
  deleteTeamMember: (id) =>
    set((state) => ({ team: state.team.filter((member) => member.id !== id) })),

  updateSettings: (payload) => set({ settings: payload }),
}));
