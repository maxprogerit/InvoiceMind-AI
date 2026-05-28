import { create } from "zustand";

interface User {
  id: number;
  email: string;
  fullName: string;
  role: string;
}

interface AuthState {
  token: string | null;
  user: User | null;
  isAuthenticated: boolean;
  setToken: (token: string, user: User) => void;
  logout: () => void;
  initialize: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  isAuthenticated: false,
  setToken: (token, user) => {
    localStorage.setItem("invoicemind-token", token);
    localStorage.setItem("invoicemind-user", JSON.stringify(user));
    set({ token, user, isAuthenticated: true });
  },
  logout: () => {
    localStorage.removeItem("invoicemind-token");
    localStorage.removeItem("invoicemind-user");
    set({ token: null, user: null, isAuthenticated: false });
  },
  initialize: () => {
    const token = localStorage.getItem("invoicemind-token");
    const userStr = localStorage.getItem("invoicemind-user");
    if (token && userStr) {
      try {
        const user = JSON.parse(userStr) as User;
        set({ token, user, isAuthenticated: true });
      } catch {
        set({ token: null, user: null, isAuthenticated: false });
      }
    }
  },
}));
