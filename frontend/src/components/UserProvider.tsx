"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import {
  accessToken,
  api,
  AUTH_EXPIRED_EVENT,
  currentUserId,
  errorMessage,
  govLogin,
  identityProviderLabel,
  logout as clearLogin,
  wasLoggedOut,
} from "@/lib/api";
import type { User } from "@/lib/types";

interface UserContextValue {
  users: User[];
  current: User | null;
  loading: boolean;
  error: string | null;
  /** Etykieta dostawcy tożsamości bieżącej sesji (SSO / Węzeł Krajowy — symulacja). */
  identityProvider: string | null;
  /** Okno logowania — otwierane ręcznie, po wylogowaniu i po wygaśnięciu sesji. */
  loginOpen: boolean;
  sessionExpired: boolean;
  openLogin: () => void;
  closeLogin: () => void;
  switchTo: (user: User, providerLabel?: string) => Promise<void>;
  logout: () => void;
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
  const [loginOpen, setLoginOpen] = useState(false);
  const [sessionExpired, setSessionExpired] = useState(false);

  /** gov-login → JWT. Błąd logowania jest pokazywany — bez cichego przejścia na nagłówek demo. */
  const signIn = useCallback(async (user: User, providerLabel?: string) => {
    try {
      await govLogin(user.id, providerLabel);
      setIdentityProvider(identityProviderLabel());
      setCurrent(user);
      setSessionExpired(false);
      setLoginOpen(false);
      setError(null);
    } catch (e) {
      setError(`Logowanie nie powiodło się: ${errorMessage(e)}`);
      setLoginOpen(true);
    }
  }, []);

  const reloadUsers = useCallback(async () => {
    try {
      const list = await api<User[]>("/api/auth/demo-users");
      setUsers(list);
      setError(null);
      const stored = list.find((u) => u.id === Number(currentUserId()));
      if (stored && accessToken()) {
        setIdentityProvider(identityProviderLabel());
        setCurrent(stored);
      } else if (wasLoggedOut()) {
        setLoginOpen(true);
      } else {
        // Pierwsze uruchomienie — automatyczne logowanie jako Kierownik, bez ściany logowania dla jury.
        const chosen = list.find((u) => u.role === DEFAULT_ROLE) ?? list[0];
        if (chosen) await signIn(chosen);
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [signIn]);

  useEffect(() => {
    void reloadUsers();
  }, [reloadUsers]);

  useEffect(() => {
    const onExpired = () => {
      setCurrent(null);
      setIdentityProvider(null);
      setSessionExpired(true);
      setLoginOpen(true);
    };
    window.addEventListener(AUTH_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onExpired);
  }, []);

  const logout = useCallback(() => {
    clearLogin();
    setCurrent(null);
    setIdentityProvider(null);
    setSessionExpired(false);
    setLoginOpen(true);
  }, []);

  const value = useMemo<UserContextValue>(() => {
    const byId = (id: number | null | undefined) => users.find((u) => u.id === id);
    return {
      users,
      current,
      loading,
      error,
      identityProvider,
      loginOpen,
      sessionExpired,
      openLogin: () => setLoginOpen(true),
      closeLogin: () => setLoginOpen(false),
      switchTo: signIn,
      logout,
      userById: byId,
      userName: (id) => byId(id)?.fullName ?? (id ? `#${id}` : "—"),
      reloadUsers,
    };
  }, [users, current, loading, error, identityProvider, loginOpen, sessionExpired, signIn, logout, reloadUsers]);

  return <UserContext.Provider value={value}>{children}</UserContext.Provider>;
}

export function useUser(): UserContextValue {
  const ctx = useContext(UserContext);
  if (!ctx) throw new Error("useUser musi być użyty wewnątrz UserProvider");
  return ctx;
}
