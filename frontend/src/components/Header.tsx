"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { formatDate, ROLE_LABEL, ROLE_ORDER, roleLabel } from "@/lib/labels";
import type { NotificationItem, Role, User } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";
import { LoginModal } from "./LoginModal";
import { useUser } from "./UserProvider";

const NAV: { href: string; label: string; roles?: Role[] }[] = [
  { href: "/", label: "Pulpit" },
  { href: "/hr", label: "Panel Kadr", roles: ["HR"] },
  { href: "/stats", label: "Statystyki", roles: ["DG", "HR"] },
  { href: "/audit", label: "Audyt", roles: ["HR", "DG", "KPRM_ADMIN"] },
  { href: "/admin", label: "Prawo jako kod", roles: ["KPRM_ADMIN"] },
  { href: "/wellbeing", label: "Dobrostan" },
];

export function Header() {
  const { users, current, switchTo, identityProvider, loginOpen, openLogin, closeLogin, logout } = useUser();
  const pathname = usePathname();
  const quick = ROLE_ORDER.map((role) => users.find((u) => u.role === role)).filter((u): u is User => !!u);

  return (
    <header className="bg-white">
      <div aria-hidden className="h-1.5 bg-gov-red" />
      <div className="border-b border-slate-200">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 py-3">
          <Link href="/" className="flex items-center gap-3 focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue">
            <span aria-hidden className="flex h-10 w-7 flex-col overflow-hidden rounded-sm ring-1 ring-slate-300">
              <span className="h-1/2 bg-white" />
              <span className="h-1/2 bg-gov-red" />
            </span>
            <span>
              <span className="block text-xs font-semibold uppercase tracking-wide text-slate-700">
                Rzeczpospolita Polska · Służba Cywilna
              </span>
              <span className="block text-xl font-extrabold tracking-[0.15em] text-gov-blue">MERITUM</span>
            </span>
          </Link>

          <div className="flex flex-wrap items-center gap-3">
            {current && <NotificationBell userId={current.id} />}
            <div className="text-right text-xs text-slate-700">
              {current ? (
                <>
                  <span className="block font-semibold text-gov-text">{current.fullName}</span>
                  <span>
                    {roleLabel(current)}
                    {current.officeName ? ` · ${current.officeName}` : ""}
                    {current.civilServant ? " · urzędnik SC" : ""}
                    {identityProvider ? ` · ${identityProvider}` : ""}
                  </span>
                </>
              ) : (
                "Niezalogowano"
              )}
            </div>
            <button
              type="button"
              onClick={openLogin}
              className="rounded border border-gov-blue px-3 py-1.5 text-sm font-bold text-gov-blue hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue"
            >
              Logowanie / SSO
            </button>
            {current && (
              <button
                type="button"
                onClick={logout}
                className="rounded px-3 py-1.5 text-sm font-bold text-gov-text ring-1 ring-inset ring-slate-300 hover:bg-gov-section focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue"
              >
                Wyloguj
              </button>
            )}
          </div>
        </div>
      </div>

      <div className="border-b border-slate-200 bg-gov-section">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2 px-4">
          <nav aria-label="Główna nawigacja">
            <ul className="flex flex-wrap">
              {NAV.filter((n) => !n.roles || (current && n.roles.includes(current.role))).map((n) => {
                const active = n.href === "/" ? pathname === "/" || pathname.startsWith("/cycles") : pathname.startsWith(n.href);
                return (
                  <li key={n.href}>
                    <Link
                      href={n.href}
                      aria-current={active ? "page" : undefined}
                      className={`block border-b-4 px-3 py-3 text-sm font-bold focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-gov-blue ${
                        active ? "border-gov-blue text-gov-blue" : "border-transparent text-gov-text hover:text-gov-blue"
                      }`}
                    >
                      {n.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>

          <div role="group" aria-label="Szybka zmiana roli demo" className="flex flex-wrap items-center gap-1 py-2 text-xs">
            <span className="mr-1 text-slate-700">Rola demo:</span>
            {quick.map((u) => {
              const active = current?.id === u.id;
              return (
                <button
                  key={u.id}
                  type="button"
                  onClick={() => void switchTo(u)}
                  aria-pressed={active}
                  title={u.positionTitle ? `${u.fullName} — ${u.positionTitle}` : u.fullName}
                  className={`rounded px-2 py-0.5 font-semibold focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue ${
                    active ? "bg-gov-blue text-white" : "text-gov-blue ring-1 ring-inset ring-slate-300 hover:bg-gov-light"
                  }`}
                >
                  {ROLE_LABEL[u.role]}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {loginOpen && <LoginModal onClose={closeLogin} />}
    </header>
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
  const pathname = usePathname();
  const items = notifications.data ?? [];

  useEffect(() => setOpen(false), [pathname]);
  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-haspopup="true"
        aria-label={`Powiadomienia: ${items.length}`}
        className="relative rounded p-2 text-gov-blue hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue"
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
                    href={`/cycles/${n.cycleId}`}
                    className="flex gap-3 px-4 py-3 text-sm hover:bg-gov-light focus:outline-none focus-visible:bg-gov-light"
                  >
                    <span aria-hidden className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${SEVERITY_DOT[n.severity]}`} />
                    <span>
                      <span className="block text-gov-text">{n.message}</span>
                      <span className="block text-xs text-slate-600">
                        Cykl #{n.cycleId}
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
