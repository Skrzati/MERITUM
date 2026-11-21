"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ROLE_LABEL, ROLE_ORDER } from "@/lib/labels";
import type { Role, User } from "@/lib/types";
import { useUser } from "./UserProvider";

const NAV: { href: string; label: string; roles?: Role[] }[] = [
  { href: "/", label: "Pulpit" },
  { href: "/hr", label: "Panel Kadr", roles: ["HR"] },
  { href: "/stats", label: "Statystyki", roles: ["DG", "HR"] },
  { href: "/admin", label: "Prawo jako kod", roles: ["KPRM_ADMIN"] },
];

export function Header() {
  const { users, current, switchTo } = useUser();
  const pathname = usePathname();
  const quick = ROLE_ORDER.map((role) => users.find((u) => u.role === role)).filter((u): u is User => !!u);

  return (
    <header className="border-b border-navy-800 bg-navy-900 text-white">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-4 py-3">
        <Link href="/" className="group">
          <span className="block text-2xl font-extrabold tracking-[0.2em]">MERITUM</span>
          <span className="block text-xs text-navy-100">Cyfrowy Asystent Ocen Okresowych</span>
        </Link>

        <div className="flex flex-col items-end gap-2">
          <div role="group" aria-label="Logowanie 1 kliknięciem" className="flex flex-wrap justify-end gap-1">
            {quick.map((u) => {
              const active = current?.id === u.id;
              return (
                <button
                  key={u.id}
                  type="button"
                  onClick={() => switchTo(u)}
                  aria-pressed={active}
                  title={`${u.fullName} (${u.email})`}
                  className={`rounded px-2.5 py-1 text-xs font-semibold focus:outline-none focus-visible:ring-2 focus-visible:ring-white ${
                    active ? "bg-white text-navy-900" : "bg-navy-700 text-white hover:bg-navy-600"
                  }`}
                >
                  {ROLE_LABEL[u.role]}
                </button>
              );
            })}
            {users.length > quick.length && (
              <select
                aria-label="Wybierz innego użytkownika"
                className="rounded bg-navy-700 px-2 py-1 text-xs text-white"
                value={current?.id ?? ""}
                onChange={(e) => {
                  const u = users.find((x) => x.id === Number(e.target.value));
                  if (u) switchTo(u);
                }}
              >
                {users.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.fullName} — {ROLE_LABEL[u.role]}
                  </option>
                ))}
              </select>
            )}
          </div>
          {current && (
            <p className="text-xs text-navy-100">
              Zalogowano jako <strong className="text-white">{current.fullName}</strong> · {ROLE_LABEL[current.role]}
              {current.civilServant ? " · urzędnik SC" : ""}
            </p>
          )}
        </div>
      </div>

      <nav aria-label="Główna nawigacja" className="bg-navy-800">
        <ul className="mx-auto flex max-w-7xl gap-1 px-4">
          {NAV.filter((n) => !n.roles || (current && n.roles.includes(current.role))).map((n) => {
            const active = n.href === "/" ? pathname === "/" || pathname.startsWith("/cycles") : pathname.startsWith(n.href);
            return (
              <li key={n.href}>
                <Link
                  href={n.href}
                  aria-current={active ? "page" : undefined}
                  className={`block border-b-2 px-3 py-2 text-sm font-medium ${
                    active ? "border-white text-white" : "border-transparent text-navy-100 hover:text-white"
                  }`}
                >
                  {n.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
    </header>
  );
}
