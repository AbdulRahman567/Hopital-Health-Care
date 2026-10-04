"use client";

import { cn } from "@/lib/utils";
import { Button } from "./button";

export interface ErrorStateProps {
  title: string;
  description?: string;
  traceId?: string;
  onRetry?: () => void;
  className?: string;
}

export function ErrorState({
  title,
  description,
  traceId,
  onRetry,
  className,
}: ErrorStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-4 py-12 text-center",
        className
      )}
      role="alert"
    >
      <div>
        <h3 className="text-lg font-medium text-destructive">{title}</h3>
        {description && (
          <p className="mt-1 text-sm text-muted-foreground">{description}</p>
        )}
        {traceId && (
          <p className="mt-1 text-xs text-muted-foreground">
            Trace ID: {traceId}
          </p>
        )}
      </div>
      {onRetry && (
        <Button variant="outline" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
