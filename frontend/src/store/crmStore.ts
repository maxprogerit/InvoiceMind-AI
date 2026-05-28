import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import { DEMO_ACCOUNT, createEmptyWorkspace, demoUser, demoWorkspace } from "../data/mockData";
import type {
  ActivityItem,
  AppSettings,
  AssistantMessage,
  CalendarEvent,
  Client,
  Company,
  Contact,
  Deal,
  DocumentCategory,
  DocumentItem,
  Invoice,
  NotificationItem,
  Receipt,
  TaskItem,
  TeamMember,
  Toast,
  ToastType,
  User,
  WorkspaceData,
} from "../types/types";
import { uid } from "../utils/helpers";

type AuthPayload = Pick<User, "email" | "password">;
type RegisterPayload = Pick<User, "name" | "email" | "password">;

interface CRMState {
  users: User[];
  workspaces: Record<string, WorkspaceData>;
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
  tasks: TaskItem[];
  team: TeamMember[];
  settings: AppSettings;
  notifications: NotificationItem[];
  assistantMessages: AssistantMessage[];
  activity: ActivityItem[];
  assistantOpen: boolean;
  assistantTyping: boolean;
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
  addDocument: (payload: Omit<DocumentItem, "id" | "uploadDate">) => void;
  addDocumentFile: (file: File, linkedClientId: string, linkedCompanyId: string, type: DocumentCategory) => void;
  deleteDocument: (id: string) => void;
  addInvoice: (payload: Omit<Invoice, "id">) => void;
  updateInvoice: (id: string, payload: Omit<Invoice, "id">) => void;
  deleteInvoice: (id: string) => void;
  addReceipt: (payload: Omit<Receipt, "id">) => void;
  updateReceipt: (id: string, payload: Omit<Receipt, "id">) => void;
  deleteReceipt: (id: string) => void;
  addTask: (payload: Omit<TaskItem, "id">) => void;
  updateTask: (id: string, payload: Omit<TaskItem, "id">) => void;
  deleteTask: (id: string) => void;
  addTeamMember: (payload: Omit<TeamMember, "id">) => void;
  updateTeamMember: (id: string, payload: Omit<TeamMember, "id">) => void;
  deleteTeamMember: (id: string) => void;
  updateSettings: (payload: AppSettings) => void;
  toggleAssistant: () => void;
  setAssistantOpen: (open: boolean) => void;
  sendAssistantMessage: (prompt: string) => Promise<void>;
}

const emptyWorkspace = createEmptyWorkspace();

const initialState = {
  users: [demoUser],
  workspaces: { [demoUser.id]: demoWorkspace },
  currentUser: null as User | null,
  isAuthenticated: false,
  clients: [] as Client[],
  deals: [] as Deal[],
  contacts: [] as Contact[],
  companies: [] as Company[],
  events: [] as CalendarEvent[],
  documents: [] as DocumentItem[],
  invoices: [] as Invoice[],
  receipts: [] as Receipt[],
  tasks: [] as TaskItem[],
  team: [] as TeamMember[],
  settings: emptyWorkspace.settings,
  notifications: [] as NotificationItem[],
  assistantMessages: emptyWorkspace.assistantMessages,
  activity: [] as ActivityItem[],
  assistantOpen: false,
  assistantTyping: false,
  toasts: [] as Toast[],
};

function mapWorkspace(workspace: WorkspaceData) {
  return {
    clients: workspace.clients,
    deals: workspace.deals,
    contacts: workspace.contacts,
    companies: workspace.companies,
    events: workspace.calendarEvents,
    documents: workspace.documents,
    invoices: workspace.invoices,
    receipts: workspace.receipts,
    tasks: workspace.tasks,
    team: workspace.teamMembers,
    settings: workspace.settings,
    notifications: workspace.notifications,
    assistantMessages: workspace.assistantMessages,
    activity: workspace.activity,
  };
}

function mutateItem<T extends { id: string }>(items: T[], id: string, payload: Omit<T, "id">): T[] {
  return items.map((item) => (item.id === id ? ({ id, ...payload } as T) : item));
}

function nowISO(): string {
  return new Date().toISOString();
}

function countBusinessDaysUntil(date: string): number {
  const target = new Date(date);
  const current = new Date();
  const delta = target.getTime() - current.getTime();
  return Math.floor(delta / (1000 * 60 * 60 * 24));
}

function buildAssistantReply(prompt: string, state: CRMState): string {
  const query = prompt.toLowerCase();
  const overdueInvoices = state.invoices.filter((invoice) => invoice.status === "Overdue");
  const topClients = [...state.clients]
    .sort((a, b) => b.estimatedValue - a.estimatedValue)
    .slice(0, 3)
    .map((client) => `${client.name} (${client.estimatedValue.toLocaleString()})`);
  const stageSummary = ["New", "Qualified", "Proposal", "Negotiation", "Won", "Lost"]
    .map((stage) => `${stage}: ${state.deals.filter((deal) => deal.stage === stage).length}`)
    .join(", ");
  const forecast = state.deals.reduce((sum, deal) => sum + (deal.value * deal.probability) / 100, 0);
  const missingDocs = state.clients.filter(
    (client) => !state.documents.some((doc) => doc.linkedClientId === client.id)
  );

  if (query.includes("overdue")) {
    return overdueInvoices.length
      ? `Found ${overdueInvoices.length} overdue invoice(s): ${overdueInvoices
          .map((invoice) => `${invoice.invoiceNumber} due in ${countBusinessDaysUntil(invoice.dueDate)} day(s)`)
          .join(", ")}.`
      : "Great news: there are no overdue invoices right now.";
  }
  if (query.includes("top client")) {
    return topClients.length ? `Top clients by estimated value: ${topClients.join(", ")}.` : "No client value data yet.";
  }
  if (query.includes("pipeline")) {
    return `Pipeline snapshot -> ${stageSummary}. Forecast revenue is ${forecast.toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}.`;
  }
  if (query.includes("dashboard")) {
    const revenue = state.invoices.filter((invoice) => invoice.status === "Paid").reduce((sum, invoice) => sum + invoice.total, 0);
    const expenses = state.receipts.reduce((sum, receipt) => sum + receipt.amount, 0);
    return `Dashboard summary: Revenue ${revenue.toLocaleString()}, Expenses ${expenses.toLocaleString()}, Active deals ${state.deals.filter((deal) => deal.stage !== "Lost" && deal.stage !== "Won").length}, Upcoming events ${state.events.filter((event) => event.date >= new Date().toISOString().slice(0, 10)).length}.`;
  }
  if (query.includes("follow")) {
    const followUps = state.clients
      .filter((client) => countBusinessDaysUntil(client.lastContactDate) < -7)
      .map((client) => client.name)
      .slice(0, 5);
    return followUps.length
      ? `Suggested follow-ups: ${followUps.join(", ")}.`
      : "No stale client follow-ups detected this week.";
  }
  if (query.includes("missing document")) {
    return missingDocs.length
      ? `Clients missing documents: ${missingDocs.map((client) => client.name).join(", ")}.`
      : "All clients have at least one linked document.";
  }
  if (query.includes("forecast")) {
    return `Forecast revenue based on deal probability is ${forecast.toLocaleString(undefined, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}.`;
  }
  return "I can help with dashboard summary, overdue invoices, top clients, pipeline analysis, follow-ups, missing documents, and revenue forecast.";
}

export const useCRMStore = create<CRMState>()(
  persist(
    (set, get) => {
      const setWorkspace = (workspace: WorkspaceData) => {
        set((state) => {
          if (!state.currentUser) return state;
          return {
            ...mapWorkspace(workspace),
            workspaces: { ...state.workspaces, [state.currentUser.id]: workspace },
          };
        });
      };

      const updateWorkspace = (updater: (workspace: WorkspaceData) => WorkspaceData) => {
        const state = get();
        if (!state.currentUser) return;
        const currentWorkspace = state.workspaces[state.currentUser.id] ?? createEmptyWorkspace();
        setWorkspace(updater(currentWorkspace));
      };

      const pushActivity = (workspace: WorkspaceData, text: string, entity: ActivityItem["entity"], entityId: string) => ({
        ...workspace,
        activity: [{ id: uid("activity"), text, entity, entityId, createdAt: nowISO() }, ...workspace.activity].slice(0, 100),
      });

      return {
        ...initialState,

        login: ({ email, password }) => {
          const user = get().users.find((item) => item.email.toLowerCase() === email.toLowerCase() && item.password === password);
          if (!user) throw new Error("Invalid email or password.");
          const workspace = get().workspaces[user.id] ?? createEmptyWorkspace(`${user.name} Workspace`);
          set({
            currentUser: user,
            isAuthenticated: true,
            ...mapWorkspace(workspace),
            workspaces: { ...get().workspaces, [user.id]: workspace },
          });
        },

        register: ({ name, email, password }) => {
          const normalizedEmail = email.trim().toLowerCase();
          if (!/\S+@\S+\.\S+/.test(normalizedEmail)) throw new Error("Please enter a valid email.");
          if (password.length < 6) throw new Error("Password must be at least 6 characters.");
          if (get().users.some((user) => user.email.toLowerCase() === normalizedEmail)) throw new Error("Email already exists.");
          const user: User = {
            id: uid("user"),
            name: name.trim(),
            email: normalizedEmail,
            password,
            createdAt: nowISO(),
          };
          const workspace = createEmptyWorkspace(`${name.trim()} Workspace`);
          workspace.settings.profileName = name.trim();
          set((state) => ({
            users: [...state.users, user],
            currentUser: user,
            isAuthenticated: true,
            workspaces: { ...state.workspaces, [user.id]: workspace },
            ...mapWorkspace(workspace),
          }));
        },

        logout: () =>
          set({
            currentUser: null,
            isAuthenticated: false,
            ...mapWorkspace(createEmptyWorkspace()),
            assistantOpen: false,
            assistantTyping: false,
          }),

        pushToast: (message, type = "info") =>
          set((state) => ({ toasts: [...state.toasts, { id: uid("toast"), message, type }] })),
        removeToast: (id) =>
          set((state) => ({ toasts: state.toasts.filter((toast) => toast.id !== id) })),

        addClient: (payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, clients: [...workspace.clients, { id: uid("client"), ...payload }] },
              `Client ${payload.name} created`,
              "client",
              payload.name
            )
          ),
        updateClient: (id, payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, clients: mutateItem(workspace.clients, id, payload) },
              `Client ${payload.name} updated`,
              "client",
              id
            )
          ),
        deleteClient: (id) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, clients: workspace.clients.filter((client) => client.id !== id) },
              "Client deleted",
              "client",
              id
            )
          ),

        addDeal: (payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, deals: [...workspace.deals, { id: uid("deal"), ...payload }] },
              `Deal ${payload.title} created`,
              "deal",
              payload.title
            )
          ),
        updateDeal: (id, payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, deals: mutateItem(workspace.deals, id, payload) },
              `Deal moved to ${payload.stage}`,
              "deal",
              id
            )
          ),
        deleteDeal: (id) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, deals: workspace.deals.filter((deal) => deal.id !== id) },
              "Deal deleted",
              "deal",
              id
            )
          ),

        addContact: (payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, contacts: [...workspace.contacts, { id: uid("contact"), ...payload }] },
              `Contact ${payload.name} created`,
              "contact",
              payload.name
            )
          ),
        updateContact: (id, payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, contacts: mutateItem(workspace.contacts, id, payload) },
              `Contact ${payload.name} updated`,
              "contact",
              id
            )
          ),
        deleteContact: (id) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, contacts: workspace.contacts.filter((contact) => contact.id !== id) },
              "Contact deleted",
              "contact",
              id
            )
          ),

        addCompany: (payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, companies: [...workspace.companies, { id: uid("company"), ...payload }] },
              `Company ${payload.name} created`,
              "company",
              payload.name
            )
          ),
        updateCompany: (id, payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, companies: mutateItem(workspace.companies, id, payload) },
              `Company ${payload.name} updated`,
              "company",
              id
            )
          ),
        deleteCompany: (id) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, companies: workspace.companies.filter((company) => company.id !== id) },
              "Company deleted",
              "company",
              id
            )
          ),

        addEvent: (payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, calendarEvents: [...workspace.calendarEvents, { id: uid("event"), ...payload }] },
              `Event ${payload.title} created`,
              "event",
              payload.title
            )
          ),
        updateEvent: (id, payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, calendarEvents: mutateItem(workspace.calendarEvents, id, payload) },
              `Event ${payload.title} updated`,
              "event",
              id
            )
          ),
        deleteEvent: (id) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, calendarEvents: workspace.calendarEvents.filter((event) => event.id !== id) },
              "Event deleted",
              "event",
              id
            )
          ),

        addDocument: (payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              {
                ...workspace,
                documents: [...workspace.documents, { id: uid("doc"), uploadDate: new Date().toISOString().slice(0, 10), ...payload }],
              },
              `Document ${payload.fileName} uploaded`,
              "document",
              payload.fileName
            )
          ),
        addDocumentFile: (file, linkedClientId, linkedCompanyId, type) =>
          updateWorkspace((workspace) =>
            pushActivity(
              {
                ...workspace,
                documents: [
                  ...workspace.documents,
                  {
                    id: uid("doc"),
                    type,
                    fileName: file.name,
                    fileSize: file.size,
                    uploadDate: new Date().toISOString().slice(0, 10),
                    linkedClientId,
                    linkedCompanyId,
                  },
                ],
              },
              `Document ${file.name} uploaded`,
              "document",
              file.name
            )
          ),
        deleteDocument: (id) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, documents: workspace.documents.filter((document) => document.id !== id) },
              "Document deleted",
              "document",
              id
            )
          ),

        addInvoice: (payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, invoices: [...workspace.invoices, { id: uid("invoice"), ...payload }] },
              `Invoice ${payload.invoiceNumber} created`,
              "invoice",
              payload.invoiceNumber
            )
          ),
        updateInvoice: (id, payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, invoices: mutateItem(workspace.invoices, id, payload) },
              `Invoice ${payload.invoiceNumber} updated`,
              "invoice",
              id
            )
          ),
        deleteInvoice: (id) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, invoices: workspace.invoices.filter((invoice) => invoice.id !== id) },
              "Invoice deleted",
              "invoice",
              id
            )
          ),

        addReceipt: (payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, receipts: [...workspace.receipts, { id: uid("receipt"), ...payload }] },
              `Receipt ${payload.receiptNumber} created`,
              "receipt",
              payload.receiptNumber
            )
          ),
        updateReceipt: (id, payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, receipts: mutateItem(workspace.receipts, id, payload) },
              `Receipt ${payload.receiptNumber} updated`,
              "receipt",
              id
            )
          ),
        deleteReceipt: (id) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, receipts: workspace.receipts.filter((receipt) => receipt.id !== id) },
              "Receipt deleted",
              "receipt",
              id
            )
          ),

        addTask: (payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, tasks: [...workspace.tasks, { id: uid("task"), ...payload }] },
              `Task ${payload.title} created`,
              "task",
              payload.title
            )
          ),
        updateTask: (id, payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, tasks: mutateItem(workspace.tasks, id, payload) },
              `Task ${payload.title} updated`,
              "task",
              id
            )
          ),
        deleteTask: (id) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, tasks: workspace.tasks.filter((task) => task.id !== id) },
              "Task deleted",
              "task",
              id
            )
          ),

        addTeamMember: (payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, teamMembers: [...workspace.teamMembers, { id: uid("team"), ...payload }] },
              `Team member ${payload.name} created`,
              "task",
              payload.name
            )
          ),
        updateTeamMember: (id, payload) =>
          updateWorkspace((workspace) =>
            pushActivity(
              { ...workspace, teamMembers: mutateItem(workspace.teamMembers, id, payload) },
              `Team member ${payload.name} updated`,
              "task",
              id
            )
          ),
        deleteTeamMember: (id) =>
          updateWorkspace((workspace) => ({ ...workspace, teamMembers: workspace.teamMembers.filter((member) => member.id !== id) })),

        updateSettings: (payload) =>
          updateWorkspace((workspace) => ({
            ...workspace,
            settings: payload,
            notifications: workspace.notifications.filter((note) =>
              payload.notifications.reminders || !note.message.toLowerCase().includes("reminder")
            ),
          })),

        toggleAssistant: () => set((state) => ({ assistantOpen: !state.assistantOpen })),
        setAssistantOpen: (open) => set({ assistantOpen: open }),

        sendAssistantMessage: async (prompt) => {
          const message = prompt.trim();
          if (!message) return;
          const userMessage: AssistantMessage = { id: uid("ai"), role: "user", content: message, createdAt: nowISO() };
          updateWorkspace((workspace) => ({ ...workspace, assistantMessages: [...workspace.assistantMessages, userMessage] }));
          set({ assistantTyping: true, assistantOpen: true });
          await new Promise((resolve) => setTimeout(resolve, 450));
          const reply: AssistantMessage = {
            id: uid("ai"),
            role: "assistant",
            content: buildAssistantReply(message, get()),
            createdAt: nowISO(),
          };
          updateWorkspace((workspace) => ({ ...workspace, assistantMessages: [...workspace.assistantMessages, reply] }));
          set({ assistantTyping: false });
        },
      };
    },
    {
      name: "clientcore-crm-store-v1",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        users: state.users,
        workspaces: state.workspaces,
        currentUser: state.currentUser,
        isAuthenticated: state.isAuthenticated,
      }),
      onRehydrateStorage: () => (state) => {
        if (!state) return;
        if (!state.users.length) state.users = [demoUser];
        if (!state.workspaces[demoUser.id]) state.workspaces[demoUser.id] = demoWorkspace;
        if (state.isAuthenticated && state.currentUser) {
          const workspace = state.workspaces[state.currentUser.id] ?? createEmptyWorkspace(`${state.currentUser.name} Workspace`);
          Object.assign(state, mapWorkspace(workspace));
        } else {
          Object.assign(state, mapWorkspace(createEmptyWorkspace()));
        }
      },
    }
  )
);

if (!useCRMStore.getState().users.some((user) => user.email === DEMO_ACCOUNT.email)) {
  useCRMStore.setState((state) => ({
    users: [...state.users, demoUser],
    workspaces: { ...state.workspaces, [demoUser.id]: state.workspaces[demoUser.id] ?? demoWorkspace },
  }));
}
