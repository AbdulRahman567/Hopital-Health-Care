"use client";

import { NavItem } from "./nav-item";
import { navigationConfig } from "@/config/navigation";
import { useAppSelector } from "@/store/hooks";
import { selectUser } from "@/store/authSlice";

export function Sidebar() {
  const user = useAppSelector(selectUser);

  return (
    <aside className="flex w-64 flex-col border-r bg-card">
      <div className="flex h-16 items-center gap-2 border-b px-6">
        <span className="text-lg font-bold">Healthcare HMS</span>
      </div>
      <div className="flex-1 overflow-auto p-4">
        <nav className="space-y-1">
          {navigationConfig.map((item) => (
            <NavItem key={item.href} {...item} />
          ))}
        </nav>
      </div>
      <div className="border-t p-4">
        <p className="text-xs text-muted-foreground">
          {user?.firstName} {user?.lastName}
        </p>
        <p className="text-xs text-muted-foreground">{user?.email}</p>
      </div>
    </aside>
  );
}
