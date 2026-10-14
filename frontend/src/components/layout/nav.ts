import { BarChart3, BookOpen, CheckCircle2, Download, FileText, ScrollText, UploadCloud, Users, type LucideIcon } from "lucide-react";

import type { Role } from "@/lib/api/types";

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Roles that may see the entry; undefined means everyone. */
  roles?: Role[];
}

export const NAV: NavItem[] = [
  { href: "/invoices", label: "Invoices", icon: FileText },
  { href: "/upload", label: "Upload", icon: UploadCloud },
  { href: "/approvals", label: "Approvals", icon: CheckCircle2, roles: ["APPROVER", "ADMIN"] },
  { href: "/analytics", label: "Analysis", icon: BarChart3 },
  { href: "/export", label: "Export", icon: Download },
  { href: "/knowledge", label: "Knowledge", icon: BookOpen, roles: ["APPROVER", "ADMIN"] },
  { href: "/audit", label: "Audit log", icon: ScrollText, roles: ["APPROVER", "ADMIN"] },
  { href: "/team", label: "Team", icon: Users, roles: ["ADMIN"] },
];
