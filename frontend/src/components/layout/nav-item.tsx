"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { PermissionGate } from "@/components/ui/permission-gate";
import type { NavItem as NavItemType } from "@/config/navigation";
import type { ReactNode } from "react";

export function NavItem({ href, label, icon: Icon, permission }: NavItemType) {
  const pathname = usePathname();
  const isActive = pathname === href || pathname.startsWith(`${href}/`);

  const content: ReactNode = (
    <Link
      href={href}
      className={cn(
        "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
        isActive
          ? "bg-primary text-primary-foreground"
          : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
      )}
    >
      <Icon className="h-4 w-4" />
      {label}
    </Link>
  );

  if (permission) {
    return (
      <PermissionGate permission={permission}>
        {content}
      </PermissionGate>
    );
  }

  return content;
}
