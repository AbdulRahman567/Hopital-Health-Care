"use client";

import { useAppSelector } from "@/store/hooks";
import { selectRoles } from "@/store/authSlice";
import { rolePermissions } from "@/lib/permissions/roles";

export function usePermissions(): string[] {
  const roles = useAppSelector(selectRoles);

  if (roles.length === 0) return [];

  return roles.flatMap((role) => {
    const perms = rolePermissions[role as keyof typeof rolePermissions];
    return perms || [];
  });
}
