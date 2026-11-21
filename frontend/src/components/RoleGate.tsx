"use client";

import { Alert, Spinner } from "@/components/ui";
import { ROLE_LABEL } from "@/lib/labels";
import type { Role } from "@/lib/types";
import { useUser } from "./UserProvider";

export function RoleGate({ roles, children }: { roles: Role[]; children: React.ReactNode }) {
  const { current, loading } = useUser();
  if (loading) return <Spinner />;
  if (!current || !roles.includes(current.role)) {
    return (
      <Alert tone="amber" title="Widok niedostępny dla aktywnej roli">
        Wymagana rola: {roles.map((r) => ROLE_LABEL[r]).join(" lub ")}. Przełącz użytkownika w nagłówku.
      </Alert>
    );
  }
  return <>{children}</>;
}
