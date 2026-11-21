"use client";

import { useEffect, useRef, useState } from "react";
import { ROLE_LABEL, ROLE_ORDER, roleLabel } from "@/lib/labels";
import type { User } from "@/lib/types";
import { Alert, Button, Field, inputClass } from "./ui";
import { useUser } from "./UserProvider";

const PROVIDERS = [
  { id: "SSO_AD", label: "Urzędowe SSO (Active Directory)", hint: "Konto domenowe urzędu — symulacja w wersji demo." },
  { id: "WK", label: "Węzeł Krajowy (login.gov.pl — symulacja)", hint: "Profil Zaufany / e-dowód / mObywatel — symulacja." },
  {
    id: "LOCAL",
    label: "Konto lokalne urzędu (login i hasło)",
    hint: "Tryb Mały Urząd — dla urzędów bez AD i Węzła Krajowego; konto zakłada administrator.",
  },
] as const;
type ProviderId = (typeof PROVIDERS)[number]["id"];

/**
 * Okno logowania: SSO i Węzeł Krajowy są symulowane (POST /api/auth/gov-login), konto lokalne weryfikuje hasło
 * w backendzie (POST /api/auth/local-login). W obu przypadkach token JWT wydaje backend.
 */
export function LoginModal({ onClose }: { onClose: () => void }) {
  const { users, current, switchTo, signInLocal, sessionExpired, error } = useUser();
  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [provider, setProvider] = useState<ProviderId>("WK");
  const [userId, setUserId] = useState<number>(current?.id ?? users[0]?.id ?? 0);
  const [busy, setBusy] = useState(false);
  const quick = ROLE_ORDER.map((role) => users.find((u) => u.role === role)).filter((u): u is User => !!u);

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);

  const label = PROVIDERS.find((p) => p.id === provider)?.label;
  const signIn = async (user: User | undefined) => {
    if (!user) return;
    setBusy(true);
    await switchTo(user, label);
    setBusy(false);
  };
  const signInWithPassword = async () => {
    setBusy(true);
    const ok = await signInLocal(login.trim(), password, label);
    if (ok) setPassword("");
    setBusy(false);
  };

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      aria-labelledby="login-title"
      className="w-full max-w-lg rounded p-0 shadow-xl backdrop:bg-slate-900/50"
    >
      <div aria-hidden className="h-1.5 bg-gov-red" />
      <div className="space-y-5 p-6">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-700">Rzeczpospolita Polska · Służba Cywilna</p>
          <h2 id="login-title" className="text-xl font-extrabold text-gov-blue">
            Logowanie do MERITUM
          </h2>
        </div>

        {sessionExpired && (
          <Alert tone="amber" title="Sesja wygasła">
            Token logowania wygasł lub został odrzucony — zaloguj się ponownie.
          </Alert>
        )}
        {error && <Alert tone="red">{error}</Alert>}

        <fieldset className="space-y-2">
          <legend className="mb-1 text-sm font-semibold text-gov-text">Sposób logowania</legend>
          {PROVIDERS.map((p) => (
            <label
              key={p.id}
              className={`flex cursor-pointer items-start gap-3 rounded border p-3 text-sm ${
                provider === p.id ? "border-gov-blue bg-gov-light" : "border-slate-300 hover:bg-gov-section"
              }`}
            >
              <input
                type="radio"
                name="identity-provider"
                className="mt-0.5 h-4 w-4 accent-navy-700"
                checked={provider === p.id}
                onChange={() => setProvider(p.id)}
              />
              <span>
                <span className="block font-bold text-gov-text">{p.label}</span>
                <span className="block text-xs text-slate-700">{p.hint}</span>
              </span>
            </label>
          ))}
        </fieldset>

        {provider === "LOCAL" ? (
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              void signInWithPassword();
            }}
          >
            <Field label="Login (adres e-mail konta)">
              <input className={inputClass} type="email" autoComplete="username" value={login} onChange={(e) => setLogin(e.target.value)} />
            </Field>
            <Field label="Hasło">
              <input
                className={inputClass}
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </Field>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={onClose}>
                Anuluj
              </Button>
              <Button type="submit" busy={busy} disabled={!login.trim() || !password}>
                Zaloguj
              </Button>
            </div>
          </form>
        ) : (
          <>
        <Field label="Konto (demo)">
          <select className={inputClass} value={userId} onChange={(e) => setUserId(Number(e.target.value))}>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName} — {roleLabel(u)}
                {u.officeName ? ` · ${u.officeName}` : ""}
              </option>
            ))}
          </select>
        </Field>

        <div>
          <p className="mb-1 text-sm font-semibold text-gov-text">Szybki wybór roli demo</p>
          <div className="flex flex-wrap gap-1">
            {quick.map((u) => (
              <button
                key={u.id}
                type="button"
                onClick={() => void signIn(u)}
                className="rounded px-2.5 py-1 text-xs font-semibold text-gov-blue ring-1 ring-inset ring-slate-300 hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue"
              >
                {ROLE_LABEL[u.role]}
              </button>
            ))}
          </div>
        </div>

        <Alert tone="blue">
          Wersja demonstracyjna: tożsamość jest symulowana, a backend wydaje token JWT (HS256) dla wybranego konta.
        </Alert>

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Anuluj
          </Button>
          <Button onClick={() => void signIn(users.find((u) => u.id === userId))} busy={busy} disabled={!userId}>
            Zaloguj
          </Button>
        </div>
          </>
        )}
      </div>
    </dialog>
  );
}
