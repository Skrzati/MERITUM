"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "@/lib/api";
import { formatDate, roleLabel, SHEET_LABEL, sheetFromAnswers, today } from "@/lib/labels";
import type { BulkCreateResult, Cycle, DuePreview, User } from "@/lib/types";
import { Alert, Badge, Button, Field, inputClass, ProblemAlert } from "./ui";
import { useUser } from "./UserProvider";

export type Mode = "single" | "group";
type GroupBy = "department" | "list";

/**
 * „Przypisz nową ocenę (utwórz cykl)” — pojedynczo (kreator arkusza § 3, podgląd terminu) albo grupowo
 * (cała komórka lub zaznaczeni pracownicy). Reguły i termin wylicza backend; UI tylko pokazuje podgląd.
 */
/** onCreated: tryb pojedynczy — po utworzeniu (np. przejście do cyklu); grupowy — okno zostaje z podsumowaniem. */
export function AssignCycleDialog({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (cycles: Cycle[], mode: Mode) => void;
}) {
  const { current, users } = useUser();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const canBulk = current?.role === "HR" || current?.role === "DG";
  const [mode, setMode] = useState<Mode>("single");

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);

  // HR / DG — pracownicy urzędu z przełożonym; kierownik — bezpośredni podwładni.
  const candidates = useMemo(() => {
    if (!current) return [];
    const office = users.filter((u) => u.officeId === current.officeId && u.id !== current.id);
    return canBulk ? office.filter((u) => u.supervisorId !== null) : office.filter((u) => u.supervisorId === current.id);
  }, [users, current, canBulk]);

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      aria-labelledby="assign-title"
      className="w-full max-w-2xl rounded p-0 shadow-xl backdrop:bg-slate-900/50"
    >
      <div aria-hidden className="h-1.5 bg-gov-blue" />
      <div className="space-y-4 p-6">
        <h2 id="assign-title" className="text-lg font-bold text-gov-text">
          Przypisz nową ocenę (utwórz cykl)
        </h2>
        {canBulk && (
          <div role="tablist" aria-label="Tryb przypisania" className="flex gap-1 border-b border-slate-200">
            {(
              [
                ["single", "Pojedynczo"],
                ["group", "Grupowo (komórka / lista)"],
              ] as [Mode, string][]
            ).map(([id, label]) => (
              <button
                key={id}
                role="tab"
                type="button"
                aria-selected={mode === id}
                onClick={() => setMode(id)}
                className={`-mb-px border-b-4 px-3 py-2 text-sm font-bold focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue ${
                  mode === id ? "border-gov-blue text-gov-blue" : "border-transparent text-gov-text hover:text-gov-blue"
                }`}
              >
                {label}
              </button>
            ))}
          </div>
        )}
        {candidates.length === 0 ? (
          <Alert tone="amber">Brak pracowników, którym możesz przypisać ocenę.</Alert>
        ) : mode === "single" ? (
          <SingleForm candidates={candidates} users={users} onClose={onClose} onCreated={onCreated} />
        ) : (
          <GroupForm candidates={candidates} onClose={onClose} onCreated={onCreated} />
        )}
      </div>
    </dialog>
  );
}

function SingleForm({
  candidates,
  users,
  onClose,
  onCreated,
}: {
  candidates: User[];
  users: User[];
  onClose: () => void;
  onCreated: (cycles: Cycle[], mode: Mode) => void;
}) {
  const [employeeId, setEmployeeId] = useState<number>(candidates[0].id);
  const employee = candidates.find((u) => u.id === employeeId);
  const [evaluatorId, setEvaluatorId] = useState<number | null>(employee?.supervisorId ?? null);
  const [startDate, setStartDate] = useState(today());
  const [place, setPlace] = useState(employee?.officeCity ?? "");
  const [reevaluation, setReevaluation] = useState(false);
  const [isHead, setIsHead] = useState(false);
  const [managesPeople, setManagesPeople] = useState(employee?.role === "EVALUATOR" || employee?.role === "DG");
  const [preview, setPreview] = useState<DuePreview | null>(null);
  const [previewError, setPreviewError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const sheet = sheetFromAnswers(isHead, managesPeople);
  const evaluators = users.filter(
    (u) => u.officeId === employee?.officeId && u.id !== employeeId && (u.role === "EVALUATOR" || u.role === "DG"),
  );

  const selectEmployee = (id: number) => {
    const e = candidates.find((u) => u.id === id);
    setEmployeeId(id);
    setEvaluatorId(e?.supervisorId ?? null);
    setPlace(e?.officeCity ?? "");
    setIsHead(false);
    setManagesPeople(e?.role === "EVALUATOR" || e?.role === "DG");
  };

  useEffect(() => {
    let cancelled = false;
    setPreviewError(null);
    api<DuePreview>(`/api/cycles/due-preview?employeeId=${employeeId}&startDate=${startDate}&reevaluation=${reevaluation}`)
      .then((p) => !cancelled && setPreview(p))
      .catch((e) => !cancelled && (setPreview(null), setPreviewError(e)));
    return () => {
      cancelled = true;
    };
  }, [employeeId, startDate, reevaluation]);

  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      const cycle = await api<Cycle>("/api/cycles", {
        body: { employeeId, sheetType: sheet, startDate, reevaluation, evaluatorId, place: place || null },
      });
      onCreated([cycle], "single");
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Oceniany pracownik">
          <select className={inputClass} value={employeeId} onChange={(e) => selectEmployee(Number(e.target.value))}>
            {candidates.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName}
                {u.positionTitle ? ` — ${u.positionTitle}` : ""}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Oceniający" hint="Domyślnie bezpośredni przełożony z danych kadrowych.">
          <select
            className={inputClass}
            value={evaluatorId ?? ""}
            onChange={(e) => setEvaluatorId(e.target.value ? Number(e.target.value) : null)}
          >
            <option value="">— wybierz —</option>
            {evaluators.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName} ({roleLabel(u)})
              </option>
            ))}
          </select>
        </Field>
        <Field label="Data rozpoczęcia okresu oceny">
          <input type="date" className={inputClass} value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        </Field>
        <Field label="Miejscowość (arkusz)" hint="Domyślnie siedziba urzędu.">
          <input className={inputClass} value={place} onChange={(e) => setPlace(e.target.value)} />
        </Field>
      </div>

      <fieldset className="rounded border border-slate-300 p-3">
        <legend className="px-1 text-sm font-bold text-gov-text">Kreator arkusza (§ 3 rozporządzenia)</legend>
        <YesNo label="1. Czy oceniany jest kierownikiem urzędu?" value={isHead} onChange={setIsHead} name="is-head" />
        {!isHead && (
          <YesNo
            label="2. Czy oceniany bezpośrednio kieruje pracownikami?"
            value={managesPeople}
            onChange={setManagesPeople}
            name="manages"
          />
        )}
        <p className="mt-2 text-sm">
          Wynik: <Badge tone="blue">Arkusz {SHEET_LABEL[sheet]}</Badge>
        </p>
      </fieldset>

      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          className="h-4 w-4 accent-navy-700"
          checked={reevaluation}
          onChange={(e) => setReevaluation(e.target.checked)}
        />
        Ponowna ocena po ocenie negatywnej (6 miesięcy, art. 81 ust. 9)
      </label>

      <ProblemAlert error={previewError} />
      {preview?.blockedReason && (
        <Alert tone="amber" title="Pracownik nie podlega ocenie okresowej">
          {preview.blockedReason}
        </Alert>
      )}
      {preview && !preview.blockedReason && (
        <Alert tone="blue" title={`Termin oceny: ${formatDate(preview.dueDate)}`}>
          Okres {preview.periodMonths} mies. wg schematu <strong>{preview.schemaVersion}</strong> (najnowsza obowiązująca wersja,
          istniejące cykle zachowują swoją — § 13). Ustalenie kryteriów i rozmowa rozpoczynająca do{" "}
          {formatDate(preview.criteriaDeadline)} (§ 7).
        </Alert>
      )}
      <ProblemAlert error={error} />
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Anuluj
        </Button>
        <Button onClick={create} busy={busy} disabled={!evaluatorId || Boolean(preview?.blockedReason)}>
          Utwórz cykl
        </Button>
      </div>
    </div>
  );
}

function YesNo({ label, value, onChange, name }: { label: string; value: boolean; onChange: (v: boolean) => void; name: string }) {
  return (
    <div className="mt-2" role="radiogroup" aria-label={label}>
      <p className="text-sm font-semibold text-gov-text">{label}</p>
      <div className="mt-1 flex gap-4 text-sm">
        {[true, false].map((v) => (
          <label key={String(v)} className="flex items-center gap-2">
            <input type="radio" name={name} className="h-4 w-4 accent-navy-700" checked={value === v} onChange={() => onChange(v)} />
            {v ? "Tak" : "Nie"}
          </label>
        ))}
      </div>
    </div>
  );
}

function GroupForm({
  candidates,
  onClose,
  onCreated,
}: {
  candidates: User[];
  onClose: () => void;
  onCreated: (cycles: Cycle[], mode: Mode) => void;
}) {
  const departments = useMemo(
    () => [...new Set(candidates.map((u) => u.department).filter((d): d is string => !!d))].sort(),
    [candidates],
  );
  const [groupBy, setGroupBy] = useState<GroupBy>("department");
  const [department, setDepartment] = useState(departments[0] ?? "");
  const [selected, setSelected] = useState<number[]>([]);
  const [startDate, setStartDate] = useState(today());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [result, setResult] = useState<BulkCreateResult | null>(null);
  const nameOf = (id: number) => candidates.find((u) => u.id === id)?.fullName ?? `#${id}`;

  const toggle = (id: number) => setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const res = await api<BulkCreateResult>("/api/cycles/bulk", {
        body: groupBy === "department" ? { department, startDate } : { employeeIds: selected, startDate },
      });
      setResult(res);
      if (res.created.length > 0) onCreated(res.created, "group");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <fieldset className="flex gap-4 text-sm">
        <legend className="sr-only">Zakres</legend>
        {(
          [
            ["department", "Cała komórka organizacyjna"],
            ["list", "Wybrani pracownicy"],
          ] as [GroupBy, string][]
        ).map(([id, label]) => (
          <label key={id} className="flex items-center gap-2">
            <input type="radio" name="group-by" className="h-4 w-4 accent-navy-700" checked={groupBy === id} onChange={() => setGroupBy(id)} />
            {label}
          </label>
        ))}
      </fieldset>
      {groupBy === "department" ? (
        <Field label="Komórka organizacyjna">
          <select className={inputClass} value={department} onChange={(e) => setDepartment(e.target.value)}>
            {departments.map((d) => (
              <option key={d} value={d}>
                {d} ({candidates.filter((u) => u.department === d).length})
              </option>
            ))}
          </select>
        </Field>
      ) : (
        <fieldset className="max-h-56 overflow-auto rounded border border-slate-300 p-2">
          <legend className="px-1 text-sm font-semibold">Pracownicy ({selected.length} zaznaczonych)</legend>
          {candidates.map((u) => (
            <label key={u.id} className="flex items-center gap-2 py-0.5 text-sm">
              <input type="checkbox" className="h-4 w-4 accent-navy-700" checked={selected.includes(u.id)} onChange={() => toggle(u.id)} />
              {u.fullName}
              {u.department ? <span className="text-xs text-slate-600">· {u.department}</span> : null}
            </label>
          ))}
        </fieldset>
      )}
      <Field label="Data rozpoczęcia okresu oceny">
        <input type="date" className={inputClass} value={startDate} onChange={(e) => setStartDate(e.target.value)} />
      </Field>
      <Alert tone="blue">
        Arkusz dobierany automatycznie (B — osoby kierujące, C — pozostali; zmiana w kreatorze pojedynczym), oceniający =
        bezpośredni przełożony. Osoby z aktywnym cyklem są pomijane.
      </Alert>
      <ProblemAlert error={error} />
      {result && (
        <Alert tone={result.skipped.length ? "amber" : "green"} title={`Utworzono cykli: ${result.created.length}`}>
          {result.skipped.length > 0 && (
            <ul className="mt-1 list-disc pl-5">
              {result.skipped.map((s) => (
                <li key={s.employeeId}>
                  {nameOf(s.employeeId)}: {s.reason}
                </li>
              ))}
            </ul>
          )}
        </Alert>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Zamknij
        </Button>
        <Button onClick={submit} busy={busy} disabled={groupBy === "department" ? !department : selected.length === 0}>
          Otwórz cykle
        </Button>
      </div>
    </div>
  );
}
