"use client";

import { useAppSelector } from "@/store/hooks";
import { selectRoles } from "@/store/authSlice";
import { rolePermissions } from "@/lib/permissions/roles";

export function usePermission(permission: string): boolean {
  const roles = useAppSelector(selectRoles);

  if (roles.length === 0) return false;

  const userPermissions = roles.flatMap((role) => {
    const perms = rolePermissions[role as keyof typeof rolePermissions];
    return perms || [];
  });

  return userPermissions.includes(permission);
}
