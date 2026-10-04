"use client";

import { cn } from "@/lib/utils";
import { AlertTriangle } from "lucide-react";

export interface Allergy {
  substance: string;
  reaction: string;
  severity: "MILD" | "MODERATE" | "SEVERE";
}

export interface AllergyBannerProps {
  allergies: Allergy[];
  className?: string;
}

export function AllergyBanner({ allergies, className }: AllergyBannerProps) {
  if (allergies.length === 0) return null;

  const severeAllergies = allergies.filter((a) => a.severity === "SEVERE");
  const hasSevere = severeAllergies.length > 0;

  return (
    <div
      className={cn(
        "rounded-lg border p-4",
        hasSevere
          ? "border-destructive/50 bg-destructive/10"
          : "border-warning/50 bg-warning/10",
        className
      )}
      role="alert"
    >
      <div className="flex items-center gap-2">
        <AlertTriangle
          className={cn(
            "h-5 w-5",
            hasSevere ? "text-destructive" : "text-warning"
          )}
        />
        <h3
          className={cn(
            "font-semibold",
            hasSevere ? "text-destructive" : "text-warning"
          )}
        >
          Allergies ({allergies.length})
        </h3>
      </div>
      <ul className="mt-2 space-y-1">
        {allergies.map((allergy, index) => (
          <li
            key={index}
            className={cn(
              "text-sm",
              allergy.severity === "SEVERE"
                ? "font-medium text-destructive"
                : "text-foreground"
            )}
          >
            {allergy.substance} — {allergy.reaction}
            {allergy.severity === "SEVERE" && " (SEVERE)"}
          </li>
        ))}
      </ul>
    </div>
  );
}
