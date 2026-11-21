"use client";

import { useEffect, useRef, useState } from "react";
import { Alert, Button, Field, inputClass, ProblemAlert } from "@/components/ui";
import { api } from "@/lib/api";
import { ROLE_LABEL } from "@/lib/labels";
import type { Role, User } from "@/lib/types";

const EDITABLE_ROLES: Role[] = ["EVALUATED", "EVALUATOR", "HR", "DG"];
const GRADES = [1, 2, 3, 4, 5, 6, 7, 8, 9];

/** Edycja danych pracownika przez kadry (PATCH /api/hr/employees/{id}); zmiana przełożonego = przekazanie cykli. */
export function EmployeeEditDialog({
  employee,
  employees,
  onClose,
  onSaved,
}: {
  employee: User;
  employees: User[];
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [form, setForm] = useState({
    firstName: employee.firstName ?? "",
    lastName: employee.lastName ?? "",
    department: employee.department ?? "",
    positionTitle: employee.positionTitle ?? "",
    role: employee.role,
    civilServant: employee.civilServant,
    civilServiceGrade: employee.civilServiceGrade ?? "",
    appointmentYear: employee.appointmentYear ?? "",
    lastGradeDate: employee.lastGradeDate ?? "",
    supervisorId: employee.supervisorId ?? "",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const supervisors = employees.filter((u) => u.id !== employee.id && (u.role === "EVALUATOR" || u.role === "DG"));
  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) => setForm((f) => ({ ...f, [key]: value }));

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      await api<User>(`/api/hr/employees/${employee.id}`, {
        method: "PATCH",
        body: {
          firstName: form.firstName || null,
          lastName: form.lastName || null,
          department: form.department || null,
          positionTitle: form.positionTitle || null,
          role: form.role,
          civilServant: form.civilServant,
          civilServiceGrade: form.civilServiceGrade === "" ? null : Number(form.civilServiceGrade),
          appointmentYear: form.appointmentYear === "" ? null : Number(form.appointmentYear),
          lastGradeDate: form.lastGradeDate || null,
          supervisorId: form.supervisorId === "" ? null : Number(form.supervisorId),
        },
      });
      await onSaved();
      onClose();
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  };

  return (
    <dialog ref={dialogRef} onClose={onClose} aria-labelledby="edit-title" className="w-full max-w-2xl rounded p-0 shadow-xl backdrop:bg-slate-900/50">
      <div aria-hidden className="h-1.5 bg-gov-blue" />
      <div className="space-y-4 p-6">
        <h2 id="edit-title" className="text-lg font-bold text-gov-text">
          Dane pracownika: {employee.fullName}
        </h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Imię">
            <input className={inputClass} value={form.firstName} onChange={(e) => set("firstName", e.target.value)} />
          </Field>
          <Field label="Nazwisko">
            <input className={inputClass} value={form.lastName} onChange={(e) => set("lastName", e.target.value)} />
          </Field>
          <Field label="Komórka organizacyjna">
            <input className={inputClass} value={form.department} onChange={(e) => set("department", e.target.value)} />
          </Field>
          <Field label="Stanowisko">
            <input className={inputClass} value={form.positionTitle} onChange={(e) => set("positionTitle", e.target.value)} />
          </Field>
          <Field label="Rola w systemie">
            <select className={inputClass} value={form.role} onChange={(e) => set("role", e.target.value as Role)}>
              {EDITABLE_ROLES.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABEL[r]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Bezpośredni przełożony" hint="Zmiana przekazuje aktywne cykle i notatki (§ 9 ust. 3).">
            <select className={inputClass} value={form.supervisorId} onChange={(e) => set("supervisorId", e.target.value === "" ? "" : Number(e.target.value))}>
              <option value="">— brak —</option>
              {supervisors.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.fullName}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <fieldset className="rounded border border-slate-300 p-3">
          <legend className="px-1 text-sm font-bold text-gov-text">Służba cywilna</legend>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="h-4 w-4 accent-navy-700" checked={form.civilServant} onChange={(e) => set("civilServant", e.target.checked)} />
            Urzędnik służby cywilnej (mianowany)
          </label>
          {form.civilServant && (
            <div className="mt-3 grid gap-4 sm:grid-cols-3">
              <Field label="Stopień służbowy">
                <select className={inputClass} value={form.civilServiceGrade} onChange={(e) => set("civilServiceGrade", e.target.value === "" ? "" : Number(e.target.value))}>
                  <option value="">—</option>
                  {GRADES.map((g) => (
                    <option key={g} value={g}>
                      {g}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Rok mianowania">
                <input type="number" min={1990} className={inputClass} value={form.appointmentYear} onChange={(e) => set("appointmentYear", e.target.value === "" ? "" : Number(e.target.value))} />
              </Field>
              <Field label="Data przyznania stopnia">
                <input type="date" className={inputClass} value={form.lastGradeDate} onChange={(e) => set("lastGradeDate", e.target.value)} />
              </Field>
            </div>
          )}
        </fieldset>
        <Alert tone="blue">Zmiany są zapisywane w dzienniku audytu (tylko nazwy pól — bez wartości danych osobowych).</Alert>
        <ProblemAlert error={error} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Anuluj
          </Button>
          <Button onClick={save} busy={busy}>
            Zapisz
          </Button>
        </div>
      </div>
    </dialog>
  );
}
