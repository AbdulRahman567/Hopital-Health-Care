"use client";

import { useState, useRef, useEffect, type ReactNode } from "react";
import { cn } from "@/lib/utils";

export interface DropdownMenuItem {
  label: string;
  onClick?: () => void;
  icon?: ReactNode;
  destructive?: boolean;
  disabled?: boolean;
}

export interface DropdownMenuProps {
  trigger: ReactNode;
  items: DropdownMenuItem[];
  align?: "start" | "end";
  className?: string;
}

export function DropdownMenu({
  trigger,
  items,
  align = "end",
  className,
}: DropdownMenuProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div ref={ref} className={cn("relative", className)}>
      <div onClick={() => setOpen(!open)}>{trigger}</div>
      {open && (
        <div
          className={cn(
            "absolute z-50 mt-1 min-w-[160px] rounded-md border bg-popover p-1 shadow-md",
            align === "end" ? "right-0" : "left-0"
          )}
        >
          {items.map((item, index) => (
            <button
              key={index}
              className={cn(
                "flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent",
                item.destructive && "text-destructive",
                item.disabled && "opacity-50 cursor-not-allowed"
              )}
              onClick={() => {
                if (!item.disabled) {
                  item.onClick?.();
                  setOpen(false);
                }
              }}
              disabled={item.disabled}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
