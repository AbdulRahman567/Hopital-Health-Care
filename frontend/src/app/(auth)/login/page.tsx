"use client";

import { LoginForm } from "@/features/auth/login-form";

export default function LoginPage() {
  return (
    <div className="w-full max-w-md space-y-8 rounded-lg border bg-card p-8 shadow-lg">
      <div className="text-center">
        <h1 className="text-2xl font-bold">Healthcare HMS</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Sign in to your account
        </p>
      </div>
      <LoginForm />
    </div>
  );
}
