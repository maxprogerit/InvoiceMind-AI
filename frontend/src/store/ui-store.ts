import { create } from "zustand";

type UiState = {
  workspace: string;
  setWorkspace: (workspace: string) => void;
};

export const useUiStore = create<UiState>((set) => ({
  workspace: "Global Finance",
  setWorkspace: (workspace) => set({ workspace })
}));
