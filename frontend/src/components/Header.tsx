"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { formatDate, ROLE_LABEL, ROLE_ORDER, roleLabel } from "@/lib/labels";
import type { NotificationItem, Role, User } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";
import { AccessibilityBar } from "./AccessibilityBar";
import { LoginModal } from "./LoginModal";
import { useUser } from "./UserProvider";

const NAV: { href: string; label: string; roles?: Role[] }[] = [
  { href: "/", label: "Pulpit" },
  { href: "/team", label: "Mój zespół", roles: ["EVALUATOR", "DG"] },
  { href: "/hr", label: "Panel Kadr", roles: ["HR"] },
  { href: "/stats", label: "Statystyki", roles: ["DG", "HR"] },
  { href: "/audit", label: "Rejestr zdarzeń", roles: ["HR", "DG", "KPRM_ADMIN"] },
  { href: "/admin", label: "Administracja", roles: ["KPRM_ADMIN"] },
  { href: "/archive", label: "Archiwum" },
  { href: "/wellbeing", label: "Dobrostan" },
];

/** Menu główne: tylko pozycje dostępne dla roli głównej lub dodatkowej, maks. 6 (czytelność). */
const MAX_NAV_ITEMS = 6;

function canSee(user: User | null, roles?: Role[]) {
  if (!roles) return true;
  if (!user) return false;
  return [user.role, ...(user.secondaryRoles ?? [])].some((r) => roles.includes(r));
}

export function Header() {
  const { current, loginOpen, closeLogin } = useUser();
  const pathname = usePathname();
  const nav = NAV.filter((n) => canSee(current, n.roles)).slice(0, MAX_NAV_ITEMS);

  return (
    <header className="bg-white">
      <div aria-hidden className="h-[3px] bg-gov-red" />

      <div className="border-b border-slate-300 bg-gov-section">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-1.5">
          <p className="text-sm text-slate-700">Serwis Rzeczypospolitej Polskiej · Służba Cywilna</p>
          <AccessibilityBar />
        </div>
      </div>

      <div className="border-b border-slate-300">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-x-8 gap-y-3 px-4 py-4">
          <Link
            href="/"
            className="flex items-center gap-3 rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue"
          >
            <span aria-hidden className="h-10 w-1 rounded-sm bg-gov-red" />
            <span className="text-2xl font-bold text-gov-text">
              <span className="tracking-[0.12em] text-gov-blue">MERITUM</span>
              <span className="font-normal"> — Oceny okresowe</span>
            </span>
          </Link>

          <nav aria-label="Główna nawigacja" className="order-last w-full lg:order-none lg:w-auto">
            <ul className="flex flex-wrap gap-x-1">
              {nav.map((n) => {
                const active = n.href === "/" ? pathname === "/" || pathname.startsWith("/cycles") : pathname.startsWith(n.href);
                return (
                  <li key={n.href}>
                    <Link
                      href={n.href}
                      aria-current={active ? "page" : undefined}
                      className={`flex min-h-[44px] items-center border-b-[3px] px-3 text-base font-bold focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-gov-blue ${
                        active ? "border-gov-blue text-gov-blue" : "border-transparent text-gov-text hover:border-slate-300 hover:text-gov-blue"
                      }`}
                    >
                      {n.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>

          <div className="flex items-center gap-2">
            {current && <NotificationBell userId={current.id} />}
            <UserMenu />
          </div>
        </div>
      </div>

      {loginOpen && <LoginModal onClose={closeLogin} />}
    </header>
  );
}

/** Zamyka rozwijane menu po kliknięciu poza nim, klawiszu Escape i zmianie strony. */
function useDismiss(open: boolean, close: () => void, ref: React.RefObject<HTMLDivElement | null>) {
  const pathname = usePathname();
  useEffect(() => close(), [pathname]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) close();
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && close();
    document.addEventListener("mousedown", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [open, close, ref]);
}

function UserMenu() {
  const { users, current, switchTo, identityProvider, openLogin, logout } = useUser();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const close = useCallback(() => setOpen(false), []);
  useDismiss(open, close, ref);
  const quick = ROLE_ORDER.map((role) => users.find((u) => u.role === role)).filter((u): u is User => !!u);

  if (!current) {
    return (
      <button
        type="button"
        onClick={openLogin}
        className="min-h-[44px] rounded bg-gov-blue px-5 text-base font-bold text-white hover:bg-gov-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue focus-visible:ring-offset-2"
      >
        Zaloguj się
      </button>
    );
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-haspopup="true"
        className="flex min-h-[44px] items-center gap-2 rounded px-3 text-left text-gov-blue ring-1 ring-inset ring-gov-blue hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue"
      >
        <svg aria-hidden viewBox="0 0 24 24" className="h-5 w-5 shrink-0" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round">
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21c0-4 4-6 8-6s8 2 8 6" />
        </svg>
        <span className="leading-tight">
          <span className="block text-base font-bold">{current.fullName}</span>
          <span className="block text-sm text-slate-700">{roleLabel(current)}</span>
        </span>
        <span aria-hidden className="text-xs">▼</span>
      </button>

      {open && (
        <div className="absolute right-0 z-40 mt-2 w-80 max-w-[calc(100vw-2rem)] rounded border border-slate-300 bg-white shadow-lg">
          <div className="border-b border-slate-300 px-4 py-3 text-sm text-slate-700">
            <p className="text-base font-bold text-gov-text">{current.fullName}</p>
            <p>
              {roleLabel(current)}
              {current.civilServant ? " · urzędnik SC" : ""}
            </p>
            {current.officeName && <p>{current.officeName}</p>}
            {identityProvider && <p>Logowanie: {identityProvider}</p>}
          </div>

          <div role="group" aria-label="Szybka zmiana roli demo" className="border-b border-slate-300 px-4 py-3">
            <p className="mb-2 text-sm font-semibold text-gov-text">Rola demo</p>
            <div className="flex flex-wrap gap-1.5">
              {quick.map((u) => {
                const active = current.id === u.id;
                return (
                  <button
                    key={u.id}
                    type="button"
                    onClick={() => void switchTo(u)}
                    aria-pressed={active}
                    title={u.positionTitle ? `${u.fullName} — ${u.positionTitle}` : u.fullName}
                    className={`min-h-[36px] rounded px-2.5 text-sm font-semibold focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue ${
                      active ? "bg-gov-blue text-white" : "text-gov-blue ring-1 ring-inset ring-slate-400 hover:bg-gov-light"
                    }`}
                  >
                    {ROLE_LABEL[u.role]}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex gap-2 px-4 py-3">
            <button
              type="button"
              onClick={() => {
                close();
                openLogin();
              }}
              className="min-h-[44px] flex-1 rounded px-3 text-base font-bold text-gov-blue ring-1 ring-inset ring-gov-blue hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue"
            >
              Zmień konto
            </button>
            <button
              type="button"
              onClick={logout}
              className="min-h-[44px] flex-1 rounded bg-gov-blue px-3 text-base font-bold text-white hover:bg-gov-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue focus-visible:ring-offset-2"
            >
              Wyloguj
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

const SEVERITY_DOT: Record<NotificationItem["severity"], string> = {
  HIGH: "bg-gov-red",
  MEDIUM: "bg-amber-500",
  LOW: "bg-gov-blue",
};

function NotificationBell({ userId }: { userId: number }) {
  const notifications = useAsync(() => api<NotificationItem[]>("/api/notifications"), [userId]);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const close = useCallback(() => setOpen(false), []);
  useDismiss(open, close, ref);
  const items = notifications.data ?? [];

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-haspopup="true"
        aria-label={`Powiadomienia: ${items.length}`}
        className="relative flex h-11 w-11 items-center justify-center rounded text-gov-blue hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue"
      >
        <svg aria-hidden viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2}>
          <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        {items.length > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-gov-red px-1 text-[11px] font-bold text-white">
            {items.length}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 z-40 mt-2 w-96 max-w-[calc(100vw-2rem)] rounded border border-slate-200 bg-white shadow-lg">
          <p className="border-b border-slate-200 px-4 py-2 text-sm font-bold text-gov-text">Powiadomienia ({items.length})</p>
          {notifications.error ? (
            <p className="px-4 py-3 text-sm text-red-800">Nie udało się pobrać powiadomień.</p>
          ) : items.length === 0 ? (
            <p className="px-4 py-3 text-sm text-slate-700">Brak powiadomień.</p>
          ) : (
            <ul className="max-h-96 overflow-auto">
              {items.map((n, i) => (
                <li key={`${n.type}-${n.cycleId}-${i}`} className="border-b border-slate-100 last:border-0">
                  <Link
                    href={n.cycleId ? `/cycles/${n.cycleId}` : "/hr"}
                    className="flex gap-3 px-4 py-3 text-sm hover:bg-gov-light focus:outline-none focus-visible:bg-gov-light"
                  >
                    <span aria-hidden className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${SEVERITY_DOT[n.severity]}`} />
                    <span>
                      <span className="block text-gov-text">{n.message}</span>
                      <span className="block text-xs text-slate-600">
                        {n.cycleId ? `Cykl #${n.cycleId}` : "Kadry"}
                        {n.dueDate ? ` · termin ${formatDate(n.dueDate)}` : ""}
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
