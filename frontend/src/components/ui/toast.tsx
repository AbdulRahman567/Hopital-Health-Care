"use client";

import { useEffect } from "react";
import { cn } from "@/lib/utils";
import { CheckCircle, XCircle, Info, X } from "lucide-react";

export interface ToastProps {
  variant?: "success" | "error" | "info";
  title: string;
  description?: string;
  traceId?: string;
  onClose?: () => void;
  duration?: number;
}

export function Toast({
  variant = "info",
  title,
  description,
  traceId,
  onClose,
  duration = 5000,
}: ToastProps) {
  useEffect(() => {
    if (duration && onClose) {
      const timer = setTimeout(onClose, duration);
      return () => clearTimeout(timer);
    }
  }, [duration, onClose]);

  const icons = {
    success: <CheckCircle className="h-5 w-5 text-success" />,
    error: <XCircle className="h-5 w-5 text-destructive" />,
    info: <Info className="h-5 w-5 text-info" />,
  };

  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-lg border bg-card p-4 shadow-lg",
        variant === "success" && "border-success/50",
        variant === "error" && "border-destructive/50",
        variant === "info" && "border-info/50"
      )}
      role="alert"
    >
      {icons[variant]}
      <div className="flex-1">
        <p className="font-medium">{title}</p>
        {description && (
          <p className="mt-1 text-sm text-muted-foreground">{description}</p>
        )}
        {traceId && (
          <p className="mt-1 text-xs text-muted-foreground">
            Trace ID: {traceId}
          </p>
        )}
      </div>
      {onClose && (
        <button
          onClick={onClose}
          className="text-muted-foreground hover:text-foreground"
        >
          <X className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}
