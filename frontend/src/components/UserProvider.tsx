"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { accessToken, api, currentUserId, errorMessage, fallbackToDemoHeader, govLogin, identityProviderLabel } from "@/lib/api";
import type { User } from "@/lib/types";

interface UserContextValue {
  users: User[];
  current: User | null;
  loading: boolean;
  error: string | null;
  /** Etykieta dostawcy tożsamości bieżącej sesji (SSO / Węzeł Krajowy — symulacja). */
  identityProvider: string | null;
  switchTo: (user: User, providerLabel?: string) => Promise<void>;
  userName: (id: number | null | undefined) => string;
  userById: (id: number | null | undefined) => User | undefined;
  reloadUsers: () => Promise<void>;
}

const UserContext = createContext<UserContextValue | null>(null);
const DEFAULT_ROLE = "EVALUATOR";

export function UserProvider({ children }: { children: React.ReactNode }) {
  const [users, setUsers] = useState<User[]>([]);
  const [current, setCurrent] = useState<User | null>(null);
  const [identityProvider, setIdentityProvider] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /** gov-login → JWT; gdy backend jest niedostępny dla logowania, zostaje fallback X-Demo-User-Id. */
  const signIn = useCallback(async (user: User, providerLabel?: string) => {
    try {
      await govLogin(user.id, providerLabel);
    } catch {
      fallbackToDemoHeader(user.id);
    }
    setIdentityProvider(identityProviderLabel());
    setCurrent(user);
  }, []);

  const reloadUsers = useCallback(async () => {
    try {
      const list = await api<User[]>("/api/auth/demo-users");
      setUsers(list);
      const storedId = Number(currentUserId());
      const stored = list.find((u) => u.id === storedId);
      const chosen = stored ?? list.find((u) => u.role === DEFAULT_ROLE) ?? list[0];
      if (chosen) {
        // Pierwsze uruchomienie (brak tokena) — automatyczne logowanie jako Kierownik, bez ściany logowania.
        if (stored && accessToken()) {
          setIdentityProvider(identityProviderLabel());
          setCurrent(stored);
        } else {
          await signIn(chosen);
        }
      }
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [signIn]);

  useEffect(() => {
    void reloadUsers();
  }, [reloadUsers]);

  const value = useMemo<UserContextValue>(() => {
    const byId = (id: number | null | undefined) => users.find((u) => u.id === id);
    return {
      users,
      current,
      loading,
      error,
      identityProvider,
      switchTo: signIn,
      userById: byId,
      userName: (id) => byId(id)?.fullName ?? (id ? `#${id}` : "—"),
      reloadUsers,
    };
  }, [users, current, loading, error, identityProvider, signIn, reloadUsers]);

  return <UserContext.Provider value={value}>{children}</UserContext.Provider>;
}

export function useUser(): UserContextValue {
  const ctx = useContext(UserContext);
  if (!ctx) throw new Error("useUser musi być użyty wewnątrz UserProvider");
  return ctx;
}
