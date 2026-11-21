"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api, currentUserId, errorMessage, USER_STORAGE_KEY } from "@/lib/api";
import type { User } from "@/lib/types";

interface UserContextValue {
  users: User[];
  current: User | null;
  loading: boolean;
  error: string | null;
  switchTo: (user: User) => void;
  userName: (id: number | null | undefined) => string;
  userById: (id: number | null | undefined) => User | undefined;
  reloadUsers: () => Promise<void>;
}

const UserContext = createContext<UserContextValue | null>(null);

export function UserProvider({ children }: { children: React.ReactNode }) {
  const [users, setUsers] = useState<User[]>([]);
  const [current, setCurrent] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reloadUsers = useCallback(async () => {
    try {
      const list = await api<User[]>("/api/auth/demo-users");
      setUsers(list);
      const storedId = Number(currentUserId());
      const chosen = list.find((u) => u.id === storedId) ?? list.find((u) => u.role === "EVALUATOR") ?? list[0];
      if (chosen) {
        window.localStorage.setItem(USER_STORAGE_KEY, String(chosen.id));
        setCurrent(chosen);
      }
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reloadUsers();
  }, [reloadUsers]);

  const switchTo = useCallback((user: User) => {
    window.localStorage.setItem(USER_STORAGE_KEY, String(user.id));
    setCurrent(user);
  }, []);

  const value = useMemo<UserContextValue>(() => {
    const byId = (id: number | null | undefined) => users.find((u) => u.id === id);
    return {
      users,
      current,
      loading,
      error,
      switchTo,
      userById: byId,
      userName: (id) => byId(id)?.fullName ?? (id ? `#${id}` : "—"),
      reloadUsers,
    };
  }, [users, current, loading, error, switchTo, reloadUsers]);

  return <UserContext.Provider value={value}>{children}</UserContext.Provider>;
}

export function useUser(): UserContextValue {
  const ctx = useContext(UserContext);
  if (!ctx) throw new Error("useUser musi być użyty wewnątrz UserProvider");
  return ctx;
}
