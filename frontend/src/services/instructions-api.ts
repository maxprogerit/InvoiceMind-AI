import { apiGet, apiPost, apiPut, apiDelete } from "./api";

export interface InstructionRule {
  id: number;
  ruleType: string;
  name: string;
  pattern: string;
  action: string;
  actionConfig: string;
  active: boolean;
  createdAt: string;
}

export const instructionsApi = {
  list: () => apiGet<InstructionRule[]>("/instructions"),
  create: (rule: Partial<InstructionRule>) => apiPost<InstructionRule>("/instructions", rule),
  update: (id: number, rule: Partial<InstructionRule>) =>
    apiPut<InstructionRule>(`/instructions/${id}`, rule),
  delete: (id: number) => apiDelete(`/instructions/${id}`),
};
