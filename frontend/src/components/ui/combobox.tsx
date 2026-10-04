"use client";

import { useState, forwardRef } from "react";
import { cn } from "@/lib/utils";
import { Check, ChevronsUpDown } from "lucide-react";

export interface ComboboxOption {
  value: string;
  label: string;
}

export interface ComboboxProps {
  options: ComboboxOption[];
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  searchPlaceholder?: string;
  className?: string;
}

export const Combobox = forwardRef<HTMLDivElement, ComboboxProps>(
  ({ options, value, onChange, placeholder, searchPlaceholder, className }, ref) => {
    const [open, setOpen] = useState(false);
    const [search, setSearch] = useState("");

    const filteredOptions = options.filter((option) =>
      option.label.toLowerCase().includes(search.toLowerCase())
    );

    const selectedOption = options.find((option) => option.value === value);

    return (
      <div ref={ref} className={cn("relative", className)}>
        <button
          type="button"
          className={cn(
            "flex h-10 w-full items-center justify-between rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
            !value && "text-muted-foreground"
          )}
          onClick={() => setOpen(!open)}
        >
          <span>{selectedOption?.label || placeholder || "Select..."}</span>
          <ChevronsUpDown className="h-4 w-4 opacity-50" />
        </button>
        {open && (
          <div className="absolute z-50 mt-1 w-full rounded-md border bg-popover p-1 shadow-md">
            <input
              type="text"
              placeholder={searchPlaceholder || "Search..."}
              className="mb-1 h-8 w-full rounded border border-input bg-background px-2 text-sm"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <div className="max-h-60 overflow-auto">
              {filteredOptions.length === 0 ? (
                <div className="px-2 py-1 text-sm text-muted-foreground">
                  No results found
                </div>
              ) : (
                filteredOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className={cn(
                      "flex w-full items-center justify-between rounded-sm px-2 py-1.5 text-sm hover:bg-accent",
                      value === option.value && "bg-accent"
                    )}
                    onClick={() => {
                      onChange?.(option.value);
                      setOpen(false);
                      setSearch("");
                    }}
                  >
                    <span>{option.label}</span>
                    {value === option.value && (
                      <Check className="h-4 w-4" />
                    )}
                  </button>
                ))
              )}
            </div>
          </div>
        )}
      </div>
    );
  }
);
Combobox.displayName = "Combobox";
