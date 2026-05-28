import { apiGet, apiPut } from "./api";

export interface UserProfile {
  id: number;
  email: string;
  name: string;
  fullName: string;
  role: string;
}

export interface OrgSettings {
  orgId: number;
  name: string;
  domain: string;
  plan: string;
  ocrEngine: string;
  autoApproveThreshold: number;
  approvalRequired: boolean;
}

export const settingsApi = {
  profile: () => apiGet<UserProfile>("/settings/profile"),
  updateProfile: (data: Partial<UserProfile>) => apiPut<UserProfile>("/settings/profile", data),
  org: () => apiGet<OrgSettings>("/settings/org"),
  updateOrg: (data: Partial<OrgSettings>) => apiPut<{ updated: boolean }>("/settings/org", data),
  apiKeys: () => apiGet<Array<{ id: number; name: string; keyPrefix: string; active: boolean }>>("/settings/api-keys"),
};
