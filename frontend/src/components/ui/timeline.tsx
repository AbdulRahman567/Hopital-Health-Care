"use client";

import { cn } from "@/lib/utils";
import { Calendar, FileText, Pill, FlaskConical, Stethoscope } from "lucide-react";

export interface TimelineEntry {
  id: string;
  type: "VISIT" | "PRESCRIPTION" | "LAB" | "DOCUMENT" | "NOTE";
  date: string;
  title: string;
  clinician: string;
  department?: string;
  description?: string;
}

const typeIcons = {
  VISIT: Stethoscope,
  PRESCRIPTION: Pill,
  LAB: FlaskConical,
  DOCUMENT: FileText,
  NOTE: Calendar,
};

const typeColors = {
  VISIT: "bg-clinical/10 text-clinical",
  PRESCRIPTION: "bg-success/10 text-success",
  LAB: "bg-info/10 text-info",
  DOCUMENT: "bg-warning/10 text-warning",
  NOTE: "bg-muted text-muted-foreground",
};

export interface TimelineProps {
  entries: TimelineEntry[];
  className?: string;
}

export function Timeline({ entries, className }: TimelineProps) {
  if (entries.length === 0) {
    return (
      <div className="py-8 text-center text-muted-foreground">
        No timeline entries
      </div>
    );
  }

  return (
    <div className={cn("space-y-4", className)}>
      {entries.map((entry, index) => {
        const Icon = typeIcons[entry.type];
        const isLast = index === entries.length - 1;

        return (
          <div key={entry.id} className="flex gap-4">
            <div className="flex flex-col items-center">
              <div
                className={cn(
                  "flex h-10 w-10 items-center justify-center rounded-full",
                  typeColors[entry.type]
                )}
              >
                <Icon className="h-5 w-5" />
              </div>
              {!isLast && (
                <div className="mt-2 w-px flex-1 bg-border" />
              )}
            </div>
            <div className="flex-1 pb-4">
              <div className="flex items-center gap-2">
                <h4 className="font-medium">{entry.title}</h4>
                <span className="text-xs text-muted-foreground">
                  {new Date(entry.date).toLocaleDateString()}
                </span>
              </div>
              <p className="text-sm text-muted-foreground">
                {entry.clinician}
                {entry.department && ` · ${entry.department}`}
              </p>
              {entry.description && (
                <p className="mt-1 text-sm">{entry.description}</p>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
