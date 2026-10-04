"use client";

import { Bell } from "lucide-react";
import { UserMenu } from "./user-menu";
import { useAppSelector } from "@/store/hooks";
import { selectUser } from "@/store/authSlice";

export function TopBar() {
  const user = useAppSelector(selectUser);

  return (
    <header className="flex h-16 items-center justify-between border-b bg-card px-6">
      <div className="flex items-center gap-4">
        <h2 className="text-lg font-semibold">
          {user ? `Welcome, ${user.firstName}` : "Dashboard"}
        </h2>
      </div>
      <div className="flex items-center gap-4">
        <button className="relative rounded-full p-2 hover:bg-accent">
          <Bell className="h-5 w-5" />
        </button>
        <UserMenu />
      </div>
    </header>
  );
}
