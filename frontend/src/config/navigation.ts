import {
  LayoutDashboard,
  Users,
  Calendar,
  Stethoscope,
  Pill,
  FlaskConical,
  FileText,
  Receipt,
  UserCog,
  Shield,
  Building,
  ScrollText,
  Settings,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  permission?: string;
}

export const navigationConfig: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard, permission: "DASHBOARD_VIEW" },
  { href: "/patients", label: "Patients", icon: Users, permission: "PATIENT_VIEW" },
  { href: "/appointments", label: "Appointments", icon: Calendar, permission: "APPOINTMENT_VIEW" },
  { href: "/clinical", label: "Clinical", icon: Stethoscope, permission: "VISIT_VIEW" },
  { href: "/prescriptions", label: "Prescriptions", icon: Pill, permission: "PRESCRIPTION_VIEW" },
  { href: "/lab", label: "Lab & Imaging", icon: FlaskConical, permission: "LAB_VIEW" },
  { href: "/documents", label: "Documents", icon: FileText, permission: "DOCUMENT_VIEW" },
  { href: "/billing", label: "Billing", icon: Receipt, permission: "BILLING_VIEW" },
  { href: "/staff", label: "Staff", icon: UserCog, permission: "STAFF_VIEW" },
  { href: "/roles", label: "Roles", icon: Shield, permission: "ROLE_VIEW" },
  { href: "/departments", label: "Departments", icon: Building, permission: "DEPARTMENT_VIEW" },
  { href: "/audit", label: "Audit", icon: ScrollText, permission: "AUDIT_VIEW" },
  { href: "/settings", label: "Settings", icon: Settings, permission: "TENANT_UPDATE" },
];
