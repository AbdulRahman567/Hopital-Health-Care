"use client";

import { Badge } from "./badge";

type StatusType =
  | "PENDING"
  | "ACTIVE"
  | "INACTIVE"
  | "SUSPENDED"
  | "SCHEDULED"
  | "CHECKED_IN"
  | "IN_CONSULTATION"
  | "COMPLETED"
  | "NO_SHOW"
  | "CANCELLED"
  | "OPEN"
  | "FINALIZED"
  | "PAID"
  | "UNPAID"
  | "PARTIAL"
  | "VERIFIED"
  | "UNVERIFIED";

const statusVariantMap: Record<StatusType, "default" | "secondary" | "destructive" | "outline" | "success" | "warning" | "info" | "clinical"> = {
  PENDING: "warning",
  ACTIVE: "success",
  INACTIVE: "secondary",
  SUSPENDED: "destructive",
  SCHEDULED: "info",
  CHECKED_IN: "clinical",
  IN_CONSULTATION: "clinical",
  COMPLETED: "success",
  NO_SHOW: "warning",
  CANCELLED: "destructive",
  OPEN: "info",
  FINALIZED: "success",
  PAID: "success",
  UNPAID: "warning",
  PARTIAL: "info",
  VERIFIED: "success",
  UNVERIFIED: "warning",
};

export interface StatusBadgeProps {
  status: StatusType;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  return (
    <Badge variant={statusVariantMap[status] || "default"} className={className}>
      {status.replace(/_/g, " ")}
    </Badge>
  );
}
