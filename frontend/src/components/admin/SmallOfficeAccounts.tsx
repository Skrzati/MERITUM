"use client";

import { useEffect, useRef, useState } from "react";
import { Alert, Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { EMPLOYMENT_STATUS_LABEL, EMPLOYMENT_STATUS_ORDER, formatDate, ROLE_LABEL } from "@/lib/labels";
import type { Account, EmploymentStatus, ImportResult, Office, Role } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const ROLES: Role[] = ["EVALUATED", "EVALUATOR", "HR", "DG"];
const EMPTY_FORM = {
  firstName: "",
  lastName: "",
  email: "",
  role: "EVALUATED" as Role,
  employmentStatus: "PRACOWNIK_SC_NIEOKRESLONY" as EmploymentStatus,
  supervisorId: "",
  department: "",
  positionTitle: "",
  password: "",
};

/**
 * Tryb Mały Urząd: konta lokalne (login + hasło BCrypt) dla urzędów bez AD / Węzła Krajowego — włączanie trybu,
 * ręczne dodawanie kont, ustawianie haseł i import CSV. Walidacja (siła hasła, role, urząd) wyłącznie w backendzie.
 */
export function SmallOfficeAccounts() {
  const offices = useAsync(() => api<Office[]>("/api/admin/offices"), []);
  const [officeId, setOfficeId] = useState<number | null>(null);
  const accounts = useAsync(
    () => (officeId ? api<Account[]>(`/api/admin/accounts?officeId=${officeId}`) : Promise.resolve([])),
    [officeId],
  );
  const [form, setForm] = useState(EMPTY_FORM);
  const [passwordFor, setPasswordFor] = useState<number | null>(null);
  const [rolesFor, setRolesFor] = useState<number | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [info, setInfo] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const office = offices.data?.find((o) => o.id === officeId);
  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) => setForm((f) => ({ ...f, [key]: value }));

  useEffect(() => {
    if (officeId === null && offices.data?.length) {
      setOfficeId((offices.data.find((o) => o.localAuthEnabled) ?? offices.data[0]).id);
    }
  }, [offices.data, officeId]);

  const run = async (key: string, fn: () => Promise<string>) => {
    setBusy(key);
    setError(null);
    setInfo(null);
    try {
      setInfo(await fn());
      await Promise.all([accounts.reload(), offices.reload()]);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const create = () =>
    run("create", async () => {
      await api<Account>("/api/admin/accounts", {
        body: {
          ...form,
          officeId,
          supervisorId: form.supervisorId === "" ? null : Number(form.supervisorId),
          password: form.password || null,
        },
      });
      setForm(EMPTY_FORM);
      return "Konto utworzone.";
    });

  const importCsv = () =>
    run("csv", async () => {
      const file = fileRef.current?.files?.[0];
      if (!file) return "Wybierz plik CSV.";
      const data = new FormData();
      data.append("file", file);
      const res = await api<ImportResult>(`/api/admin/accounts/import-csv?officeId=${officeId}`, { form: data });
      return `Zaimportowano: ${res.imported}, pominięto: ${res.skipped.length}, błędy: ${res.errors.length}.`;
    });

  const supervisors = (accounts.data ?? []).filter((a) => a.user.role === "EVALUATOR" || a.user.role === "DG");

  return (
    <Card title="Konta lokalne — tryb Mały Urząd">
      <div className="space-y-4">
        <p className="text-sm text-slate-700">
          Dla urzędów bez Active Directory i Węzła Krajowego: logowanie loginem (e-mail) i hasłem przechowywanym jako skrót BCrypt.
          Hasło: min. 12 znaków, w tym litera i cyfra.
        </p>
        {offices.loading && <Spinner />}
        <ProblemAlert error={offices.error} />
        {offices.data && (
          <div className="flex flex-wrap items-end gap-3">
            <Field label="Urząd">
              <select className={inputClass} value={officeId ?? ""} onChange={(e) => setOfficeId(Number(e.target.value))}>
                {offices.data.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.name}
                  </option>
                ))}
              </select>
            </Field>
            {office && (
              <label className="flex items-center gap-2 pb-2 text-sm">
                <input
                  type="checkbox"
                  className="h-4 w-4 accent-navy-700"
                  checked={office.localAuthEnabled}
                  disabled={busy === "toggle"}
                  onChange={(e) =>
                    void run("toggle", async () => {
                      await api<Office>(`/api/admin/offices/${office.id}/local-auth`, { method: "PATCH", body: { enabled: e.target.checked } });
                      return e.target.checked ? "Włączono logowanie kontem lokalnym." : "Wyłączono logowanie kontem lokalnym.";
                    })
                  }
                />
                Logowanie kontem lokalnym włączone
              </label>
            )}
          </div>
        )}

        {accounts.loading && <Spinner />}
        <ProblemAlert error={accounts.error} />
        {accounts.data && accounts.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-slate-300 text-xs uppercase text-slate-600">
                <tr>
                  <th className="py-2 pr-3">Konto</th>
                  <th className="py-2 pr-3">Rola / status</th>
                  <th className="py-2 pr-3">Hasło lokalne</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {accounts.data.map((a) => (
                  <tr key={a.user.id} className="border-b border-slate-100 last:border-0">
                    <td className="py-2 pr-3">
                      <span className="font-medium">{a.user.fullName}</span>
                      <span className="block text-xs text-slate-600">{a.user.email}</span>
                    </td>
                    <td className="py-2 pr-3 text-xs">
                      {ROLE_LABEL[a.user.role]}
                      {a.user.secondaryRoles?.length ? (
                        <span className="mt-1 flex flex-wrap gap-1">
                          {a.user.secondaryRoles.map((r) => (
                            <Badge key={r} tone="blue">
                              + {ROLE_LABEL[r]}
                            </Badge>
                          ))}
                        </span>
                      ) : null}
                      <span className="block text-slate-600">{EMPLOYMENT_STATUS_LABEL[a.user.employmentStatus]}</span>
                      {rolesFor === a.user.id ? (
                        <SecondaryRolesEditor
                          account={a}
                          busy={busy === "roles"}
                          onCancel={() => setRolesFor(null)}
                          onSave={(secondaryRoles) =>
                            void run("roles", async () => {
                              await api(`/api/admin/accounts/${a.user.id}/roles`, { method: "PATCH", body: { secondaryRoles } });
                              setRolesFor(null);
                              return `Zapisano role łączone: ${a.user.fullName}.`;
                            })
                          }
                        />
                      ) : (
                        <button type="button" className="mt-1 text-gov-blue underline" onClick={() => setRolesFor(a.user.id)}>
                          Role łączone
                        </button>
                      )}
                    </td>
                    <td className="py-2 pr-3">
                      {a.hasLocalPassword ? (
                        <Badge tone="green">ustawione {a.passwordChangedAt ? formatDate(a.passwordChangedAt.slice(0, 10)) : ""}</Badge>
                      ) : (
                        <Badge>brak</Badge>
                      )}
                    </td>
                    <td className="py-2 text-right">
                      {passwordFor === a.user.id ? (
                        <span className="inline-flex gap-1">
                          <input
                            aria-label={`Nowe hasło dla ${a.user.fullName}`}
                            type="password"
                            autoComplete="new-password"
                            className={`${inputClass} w-44`}
                            value={newPassword}
                            onChange={(e) => setNewPassword(e.target.value)}
                          />
                          <Button
                            busy={busy === "pwd"}
                            disabled={!newPassword}
                            onClick={() =>
                              void run("pwd", async () => {
                                await api(`/api/admin/accounts/${a.user.id}/password`, { body: { password: newPassword } });
                                setPasswordFor(null);
                                setNewPassword("");
                                return `Ustawiono hasło dla: ${a.user.fullName}.`;
                              })
                            }
                          >
                            Zapisz
                          </Button>
                        </span>
                      ) : (
                        <Button variant="secondary" onClick={() => setPasswordFor(a.user.id)}>
                          {a.hasLocalPassword ? "Zmień hasło" : "Ustaw hasło"}
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <fieldset className="rounded border border-slate-300 p-3">
          <legend className="px-1 text-sm font-bold text-gov-text">Dodaj konto ręcznie</legend>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Imię">
              <input className={inputClass} value={form.firstName} onChange={(e) => set("firstName", e.target.value)} />
            </Field>
            <Field label="Nazwisko">
              <input className={inputClass} value={form.lastName} onChange={(e) => set("lastName", e.target.value)} />
            </Field>
            <Field label="E-mail (login)">
              <input className={inputClass} type="email" value={form.email} onChange={(e) => set("email", e.target.value)} />
            </Field>
            <Field label="Rola">
              <select className={inputClass} value={form.role} onChange={(e) => set("role", e.target.value as Role)}>
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {ROLE_LABEL[r]}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Status zatrudnienia">
              <select className={inputClass} value={form.employmentStatus} onChange={(e) => set("employmentStatus", e.target.value as EmploymentStatus)}>
                {EMPLOYMENT_STATUS_ORDER.map((s) => (
                  <option key={s} value={s}>
                    {EMPLOYMENT_STATUS_LABEL[s]}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Bezpośredni przełożony">
              <select className={inputClass} value={form.supervisorId} onChange={(e) => set("supervisorId", e.target.value)}>
                <option value="">— brak —</option>
                {supervisors.map((a) => (
                  <option key={a.user.id} value={a.user.id}>
                    {a.user.fullName}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Komórka organizacyjna">
              <input className={inputClass} value={form.department} onChange={(e) => set("department", e.target.value)} />
            </Field>
            <Field label="Stanowisko">
              <input className={inputClass} value={form.positionTitle} onChange={(e) => set("positionTitle", e.target.value)} />
            </Field>
            <Field label="Hasło początkowe (opcjonalnie)" hint="Puste — konto bez logowania hasłem; hasło można ustawić później.">
              <input className={inputClass} type="password" autoComplete="new-password" value={form.password} onChange={(e) => set("password", e.target.value)} />
            </Field>
          </div>
          <div className="mt-3">
            <Button onClick={create} busy={busy === "create"} disabled={!officeId || !form.firstName.trim() || !form.lastName.trim() || !form.email.trim()}>
              Dodaj konto
            </Button>
          </div>
        </fieldset>

        <fieldset className="rounded border border-slate-300 p-3">
          <legend className="px-1 text-sm font-bold text-gov-text">Import kont (CSV)</legend>
          <Field label="Plik CSV" hint="Kolumny: full_name,email,role,employment_status|is_civil_servant[,supervisor_email]">
            <input ref={fileRef} type="file" accept=".csv,text/csv" className="text-sm" />
          </Field>
          <div className="mt-3">
            <Button variant="secondary" onClick={importCsv} busy={busy === "csv"} disabled={!officeId}>
              Importuj do wybranego urzędu
            </Button>
          </div>
        </fieldset>

        <ProblemAlert error={error} />
        {info && !error && <Alert tone="green">{info}</Alert>}
      </div>
    </Card>
  );
}

/** Role dodatkowe w małym urzędzie (np. kierownik urzędu = DG + przełożony + kadry); KPRM_ADMIN nie może być rolą dodatkową. */
function SecondaryRolesEditor({
  account,
  busy,
  onSave,
  onCancel,
}: {
  account: Account;
  busy: boolean;
  onSave: (roles: Role[]) => void;
  onCancel: () => void;
}) {
  const [roles, setRoles] = useState<Role[]>(account.user.secondaryRoles ?? []);
  const available = ROLES.filter((r) => r !== account.user.role);
  return (
    <fieldset className="mt-2 rounded border border-slate-300 p-2">
      <legend className="px-1 font-semibold">Role dodatkowe</legend>
      <div className="flex flex-wrap gap-3">
        {available.map((r) => (
          <label key={r} className="flex items-center gap-1">
            <input
              type="checkbox"
              className="h-4 w-4 accent-gov-blue"
              checked={roles.includes(r)}
              onChange={(e) => setRoles((x) => (e.target.checked ? [...x, r] : x.filter((y) => y !== r)))}
            />
            {ROLE_LABEL[r]}
          </label>
        ))}
      </div>
      <div className="mt-2 flex gap-2">
        <Button busy={busy} onClick={() => onSave(roles)}>
          Zapisz role
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Anuluj
        </Button>
      </div>
    </fieldset>
  );
}
