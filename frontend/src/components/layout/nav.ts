import { BarChart3, BookOpen, Building2, UserCog, CheckCircle2, Download, FileText, ScrollText, Settings, UploadCloud, Users, Webhook, type LucideIcon } from "lucide-react";

import type { FirmRole, Role } from "@/lib/api/types";

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Roles that may see the entry; undefined means everyone. */
  roles?: Role[];
  /** Firm roles that may see the entry; undefined means no firm is needed. Firm pages are about the whole firm, whatever the role in the current client. */
  firmRoles?: FirmRole[];
}

export const NAV: NavItem[] = [
  { href: "/invoices", label: "Invoices", icon: FileText },
  { href: "/firm/clients", label: "Firm clients", icon: Building2, firmRoles: ["FIRM_ADMIN", "ACCOUNTANT"] },
  { href: "/firm/team", label: "Firm team", icon: UserCog, firmRoles: ["FIRM_ADMIN"] },
  { href: "/upload", label: "Upload", icon: UploadCloud },
  { href: "/approvals", label: "Approvals", icon: CheckCircle2, roles: ["APPROVER", "ADMIN"] },
  { href: "/analytics", label: "Analysis", icon: BarChart3 },
  { href: "/export", label: "Export", icon: Download },
  { href: "/knowledge", label: "Knowledge", icon: BookOpen, roles: ["APPROVER", "ADMIN"] },
  { href: "/audit", label: "Audit log", icon: ScrollText, roles: ["APPROVER", "ADMIN"] },
  { href: "/team", label: "Team", icon: Users, roles: ["ADMIN"] },
  { href: "/integrations", label: "Integrations", icon: Webhook, roles: ["ADMIN"] },
  { href: "/settings", label: "Settings", icon: Settings, roles: ["ADMIN"] },
];
