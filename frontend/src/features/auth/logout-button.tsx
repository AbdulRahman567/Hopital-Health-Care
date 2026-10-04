"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useLogout } from "@/lib/api/hooks";

export function LogoutButton() {
  const router = useRouter();
  const logout = useLogout();
  const [open, setOpen] = useState(false);

  const handleLogout = async () => {
    try {
      await logout.mutateAsync();
      router.push("/login");
    } catch {
      router.push("/login");
    }
  };

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="text-sm text-muted-foreground hover:text-foreground"
      >
        Logout
      </button>
      <ConfirmDialog
        open={open}
        title="Confirm Logout"
        description="Are you sure you want to log out? Your session will be ended."
        confirmLabel="Logout"
        onConfirm={handleLogout}
        onCancel={() => setOpen(false)}
      />
    </>
  );
}
