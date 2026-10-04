"use client";

import { useSelector } from "react-redux";
import { selectUser } from "@/store/authSlice";

export default function DashboardPage() {
  const user = useSelector(selectUser);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground">
          Welcome back, {user?.firstName || "User"}
        </p>
      </div>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-lg border bg-card p-6">
          <h3 className="text-sm font-medium text-muted-foreground">
            Total Patients
          </h3>
          <p className="mt-2 text-3xl font-bold">--</p>
        </div>
        <div className="rounded-lg border bg-card p-6">
          <h3 className="text-sm font-medium text-muted-foreground">
            Today's Appointments
          </h3>
          <p className="mt-2 text-3xl font-bold">--</p>
        </div>
        <div className="rounded-lg border bg-card p-6">
          <h3 className="text-sm font-medium text-muted-foreground">
            Active Doctors
          </h3>
          <p className="mt-2 text-3xl font-bold">--</p>
        </div>
        <div className="rounded-lg border bg-card p-6">
          <h3 className="text-sm font-medium text-muted-foreground">
            Pending Bills
          </h3>
          <p className="mt-2 text-3xl font-bold">--</p>
        </div>
      </div>
    </div>
  );
}
