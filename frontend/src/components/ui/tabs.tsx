"use client";

import { useState, type ReactNode } from "react";
import { cn } from "@/lib/utils";

export interface Tab {
  value: string;
  label: string;
  content: ReactNode;
}

export interface TabsProps {
  tabs: Tab[];
  defaultValue?: string;
  className?: string;
}

export function Tabs({ tabs, defaultValue, className }: TabsProps) {
  const [activeTab, setActiveTab] = useState(defaultValue || tabs[0]?.value);

  return (
    <div className={className}>
      <div className="flex border-b">
        {tabs.map((tab) => (
          <button
            key={tab.value}
            className={cn(
              "px-4 py-2 text-sm font-medium transition-colors",
              activeTab === tab.value
                ? "border-b-2 border-primary text-primary"
                : "text-muted-foreground hover:text-foreground"
            )}
            onClick={() => setActiveTab(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="pt-4">
        {tabs.find((tab) => tab.value === activeTab)?.content}
      </div>
    </div>
  );
}
