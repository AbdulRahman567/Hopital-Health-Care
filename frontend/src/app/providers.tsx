"use client";

import { StoreProvider } from "@/store/StoreProvider";
import { QueryProvider } from "@/lib/query/QueryProvider";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <StoreProvider>
      <QueryProvider>{children}</QueryProvider>
    </StoreProvider>
  );
}
