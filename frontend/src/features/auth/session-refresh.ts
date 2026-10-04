"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { silentRefresh } from "@/lib/api/refresh";
import { store } from "@/store";
import { setSession, clearSession } from "@/store/authSlice";

export function useSessionRefresh() {
  const router = useRouter();
  const isAuthenticated = store.getState().auth.isAuthenticated;

  useEffect(() => {
    if (isAuthenticated) return;

    const attemptRefresh = async () => {
      try {
        const token = await silentRefresh();
        setSession({
          user: null as never,
          accessToken: token,
          roles: [],
        });
        router.push("/dashboard");
      } catch {
        clearSession();
        router.push("/login");
      }
    };

    attemptRefresh();
  }, [isAuthenticated, setSession, clearSession, router]);
}
