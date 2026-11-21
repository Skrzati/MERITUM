"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { dueState, type CycleDetails } from "@/components/cycle/contracts";
import { Alert, Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import { formatDate, ROLE_LABEL, STATUS_LABEL, STATUS_TONE, today } from "@/lib/labels";
import type { Schema, SheetType, User } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const SHEETS: { value: SheetType; label: string }[] = [
  { value: "A", label: "A — kierownik urzędu" },
  { value: "B", label: "B — bezpośrednie kierowanie pracownikami" },
  { value: "C", label: "C — pozostałe stanowiska" },
];

export default function DashboardPage() {
  const router = useRouter();
  const { current, users, userName, loading: userLoading, error: userError } = useUser();
  const cycles = useAsync(async () => (current ? api<CycleDetails[]>("/api/cycles") : []), [current?.id]);
  const schemaIds = useMemo(() => [...new Set((cycles.data ?? []).map((c) => c.schemaVersionId))], [cycles.data]);
  const schemas = useAsync(
    async () => Promise.all(schemaIds.map((id) => api<Schema>(`/api/schemas/${id}`))),
    [schemaIds.join(","), current?.id],
  );
  const versionOf = (id: number) => schemas.data?.find((s) => s.id === id)?.versionCode ?? `#${id}`;
  const [creating, setCreating] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);

  // HR — pracownicy urzędu z przełożonym; kierownik — wyłącznie bezpośredni podwładni.
  const candidates = useMemo(() => {
    if (!current) return [];
    if (current.role === "HR") return users.filter((u) => u.officeId === current.officeId && u.supervisorId !== null);
    return users.filter((u) => u.supervisorId === current.id);
  }, [users, current]);
  const canCreate = candidates.length > 0;

  const startNext = async (cycleId: number) => {
    setBusyId(cycleId);
    setActionError(null);
    try {
      const next = await api<CycleDetails>(`/api/cycles/${cycleId}/next`, { method: "POST" });
      router.push(`/cycles/${next.id}`);
    } catch (e) {
      setActionError(e);
      setBusyId(null);
    }
  };

  if (userLoading) return <Spinner />;
  if (userError) return <ProblemAlert error={`Brak połączenia z backend-core: ${userError}`} />;

  const now = today();
  const mayStartNext = (c: CycleDetails) => c.status === "COMPLETED" && (current?.role === "HR" || current?.id === c.evaluatorId);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-navy-900">Pulpit</h1>
          <p className="text-sm text-slate-700">
            Cykle ocen widoczne dla roli <strong>{current ? ROLE_LABEL[current.role] : "—"}</strong>.
          </p>
        </div>
        {canCreate && <Button onClick={() => setCreating(true)}>+ Nowy cykl oceny</Button>}
      </div>

      {creating && (
        <NewCycleDialog
          candidates={candidates}
          onClose={() => setCreating(false)}
          onCreated={(c) => router.push(`/cycles/${c.id}`)}
        />
      )}

      <Card title="Cykle oceny okresowej">
        {cycles.loading && <Spinner />}
        <ProblemAlert error={cycles.error} />
        <ProblemAlert error={actionError} />
        {cycles.data && cycles.data.length === 0 && <p className="text-sm text-slate-700">Brak cykli do wyświetlenia.</p>}
        {cycles.data && cycles.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Lista cykli oceny</caption>
              <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
                <tr>
                  <th className="py-2 pr-4">Oceniany</th>
                  <th className="py-2 pr-4">Oceniający</th>
                  <th className="py-2 pr-4">Arkusz</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2 pr-4">Termin oceny</th>
                  <th className="py-2 pr-4">Schemat</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {cycles.data.map((c) => {
                  const due = dueState(c, now);
                  return (
                    <tr key={c.id} className="border-b border-slate-100 align-top last:border-0">
                      <td className="py-3 pr-4 font-medium">
                        {userName(c.employeeId)}
                        {c.employeeId === current?.id && <span className="ml-2 text-xs text-slate-600">(Ty)</span>}
                        <div className="mt-1 flex flex-wrap gap-1">
                          {c.reevaluation && <Badge tone="amber">ponowna ocena (6 mies.)</Badge>}
                          {c.mandatoryGradeEligible && (
                            <Badge tone="green">Obligatoryjny stopień (art. 89 ust. 2)</Badge>
                          )}
                        </div>
                      </td>
                      <td className="py-3 pr-4">{userName(c.evaluatorId)}</td>
                      <td className="py-3 pr-4">{c.sheetType}</td>
                      <td className="py-3 pr-4">
                        <Badge tone={STATUS_TONE[c.status]}>{STATUS_LABEL[c.status]}</Badge>
                      </td>
                      <td className="py-3 pr-4">
                        <div>{formatDate(c.dueDate)}</div>
                        <div className="mt-1">
                          <Badge tone={due.tone}>{due.label}</Badge>
                        </div>
                      </td>
                      <td className="py-3 pr-4">
                        <Badge>{versionOf(c.schemaVersionId)}</Badge>
                      </td>
                      <td className="py-3 text-right">
                        <div className="flex flex-col items-end gap-2">
                          <Link
                            href={`/cycles/${c.id}`}
                            className="rounded-md bg-navy-700 px-3 py-1.5 text-sm font-semibold text-white hover:bg-navy-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-600"
                          >
                            Otwórz
                          </Link>
                          {mayStartNext(c) && (
                            <Button variant="secondary" busy={busyId === c.id} onClick={() => startNext(c.id)}>
                              Rozpocznij kolejny cykl
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}

function NewCycleDialog({
  candidates,
  onClose,
  onCreated,
}: {
  candidates: User[];
  onClose: () => void;
  onCreated: (c: CycleDetails) => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const [employeeId, setEmployeeId] = useState<number>(candidates[0]?.id ?? 0);
  const [sheetType, setSheetType] = useState<SheetType>("C");
  const [startDate, setStartDate] = useState(today());
  const [reevaluation, setReevaluation] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);

  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      onCreated(await api<CycleDetails>("/api/cycles", { body: { employeeId, sheetType, startDate, reevaluation } }));
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  };

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      aria-labelledby="new-cycle-title"
      className="w-full max-w-lg rounded-lg p-0 shadow-xl backdrop:bg-slate-900/50"
    >
      <div className="space-y-4 p-6">
        <h2 id="new-cycle-title" className="text-lg font-semibold text-navy-900">
          Nowy cykl oceny okresowej
        </h2>
        <Field label="Oceniany pracownik">
          <select className={inputClass} value={employeeId} onChange={(e) => setEmployeeId(Number(e.target.value))}>
            {candidates.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Rodzaj arkusza (§ 3)">
          <select className={inputClass} value={sheetType} onChange={(e) => setSheetType(e.target.value as SheetType)}>
            {SHEETS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Data rozpoczęcia okresu oceny">
          <input type="date" className={inputClass} value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        </Field>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="h-4 w-4 accent-navy-700"
            checked={reevaluation}
            onChange={(e) => setReevaluation(e.target.checked)}
          />
          Ponowna ocena po ocenie negatywnej (6 miesięcy, art. 81 ust. 9)
        </label>
        <Alert tone="blue">
          Cykl zawsze przypina <strong>najnowszą obowiązującą wersję schematu</strong> („prawo jako kod”), a termin
          oceny wylicza z jej okresu — istniejące cykle zachowują swoją wersję (§ 13).
        </Alert>
        <ProblemAlert error={error} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Anuluj
          </Button>
          <Button onClick={create} busy={busy} disabled={!employeeId}>
            Utwórz cykl
          </Button>
        </div>
      </div>
    </dialog>
  );
}
