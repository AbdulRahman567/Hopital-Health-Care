"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { silentRefresh } from "@/lib/api/refresh";
import { store } from "@/store";
import { setAccessToken, clearSession } from "@/store/authSlice";

export function useSessionRefresh() {
  const router = useRouter();

  useEffect(() => {
    const isAuthenticated = store.getState().auth.isAuthenticated;
    if (isAuthenticated) return;

    const attemptRefresh = async () => {
      try {
        const token = await silentRefresh();
        store.dispatch(setAccessToken(token));
        router.push("/dashboard");
      } catch {
        store.dispatch(clearSession());
        router.push("/login");
      }
    };

    attemptRefresh();
  }, [router]);
}
