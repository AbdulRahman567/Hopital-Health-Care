"use client";

import type { HTMLAttributes } from "react";
import { cn } from "@/lib/utils";

export interface AvatarProps extends HTMLAttributes<HTMLDivElement> {
  src?: string;
  alt?: string;
  fallback?: string;
}

export function Avatar({ src, alt, fallback, className, ...props }: AvatarProps) {
  return (
    <div
      className={cn(
        "flex h-10 w-10 items-center justify-center rounded-full bg-muted",
        className
      )}
      {...props}
    >
      {src ? (
        <img
          src={src}
          alt={alt || "Avatar"}
          className="h-full w-full rounded-full object-cover"
        />
      ) : (
        <span className="text-sm font-medium text-muted-foreground">
          {fallback || "?"}
        </span>
      )}
    </div>
  );
}
