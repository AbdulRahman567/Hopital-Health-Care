"use client";

import { forwardRef, type HTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/utils";
import { Label } from "./label";

export interface FormFieldProps extends HTMLAttributes<HTMLDivElement> {
  label: string;
  error?: string;
  required?: boolean;
  hint?: string;
  children: ReactNode;
  htmlFor?: string;
}

export const FormField = forwardRef<HTMLDivElement, FormFieldProps>(
  ({ className, label, error, required, hint, children, htmlFor, ...props }, ref) => {
    return (
      <div ref={ref} className={cn("space-y-2", className)} {...props}>
        <Label htmlFor={htmlFor}>
          {label}
          {required && (
            <span className="ml-1 text-destructive" aria-hidden="true">
              *
            </span>
          )}
        </Label>
        {children}
        {hint && !error && (
          <p className="text-sm text-muted-foreground">{hint}</p>
        )}
        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}
      </div>
    );
  }
);
FormField.displayName = "FormField";
