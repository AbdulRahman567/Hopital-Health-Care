"use client";

import { useRouter } from "next/navigation";
import { LogOut, User as UserIcon } from "lucide-react";
import { Avatar } from "@/components/ui/avatar";
import { DropdownMenu } from "@/components/ui/dropdown-menu";
import { useAppSelector } from "@/store/hooks";
import { selectUser } from "@/store/authSlice";
import { useLogout } from "@/lib/api/hooks";

export function UserMenu() {
  const router = useRouter();
  const user = useAppSelector(selectUser);
  const logout = useLogout();

  const handleLogout = async () => {
    try {
      await logout.mutateAsync();
      router.push("/login");
    } catch {
      router.push("/login");
    }
  };

  return (
    <DropdownMenu
      trigger={
        <button className="flex items-center gap-2 rounded-full">
          <Avatar
            fallback={user?.firstName?.[0] || user?.email?.[0] || "?"}
            className="h-8 w-8"
          />
        </button>
      }
      items={[
        {
          label: "Profile",
          icon: <UserIcon className="h-4 w-4" />,
          onClick: () => router.push("/settings"),
        },
        {
          label: "Logout",
          icon: <LogOut className="h-4 w-4" />,
          onClick: handleLogout,
          destructive: true,
        },
      ]}
    />
  );
}
