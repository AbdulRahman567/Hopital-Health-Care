"use client";

import type { ReactNode } from "react";
import { cn } from "@/lib/utils";
import { Skeleton } from "./skeleton";
import { EmptyState } from "./empty-state";
import { ErrorState } from "./error-state";

export interface Column<T> {
  key: string;
  header: string;
  accessor: (row: T) => ReactNode;
  className?: string;
}

export interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  isLoading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  emptyMessage?: string;
  className?: string;
}

export function DataTable<T extends { id: string }>({
  columns,
  data,
  isLoading,
  error,
  onRetry,
  emptyMessage = "No data available",
  className,
}: DataTableProps<T>) {
  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <ErrorState
        title="Failed to load data"
        description={error}
        onRetry={onRetry}
      />
    );
  }

  if (data.length === 0) {
    return <EmptyState title="No data" description={emptyMessage} />;
  }

  return (
    <div className={cn("overflow-x-auto", className)}>
      <table className="w-full caption-bottom text-sm">
        <thead>
          <tr className="border-b">
            {columns.map((column) => (
              <th
                key={column.key}
                className={cn(
                  "h-12 px-4 text-left align-middle font-medium text-muted-foreground",
                  column.className
                )}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row) => (
            <tr key={row.id} className="border-b transition-colors hover:bg-muted/50">
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={cn("p-4 align-middle", column.className)}
                >
                  {column.accessor(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
