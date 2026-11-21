"use client";

import { useState } from "react";
import { RoleGate } from "@/components/RoleGate";
import { Alert, Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api, saveBlob } from "@/lib/api";
import { formatDate, ROLE_LABEL, today } from "@/lib/labels";
import type { Cycle, HandoverResult, ImportResult, User } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

type SortKey = "fullName" | "email" | "role" | "id";

export default function HrPage() {
  return (
    <RoleGate roles={["HR"]}>
      <HrPanel />
    </RoleGate>
  );
}

function HrPanel() {
  const { current, reloadUsers } = useUser();
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortKey>("fullName");
  const [dir, setDir] = useState<"ASC" | "DESC">("ASC");
  const employees = useAsync(
    () => api<User[]>(`/api/hr/employees?q=${encodeURIComponent(query)}&sort=${sort}&dir=${dir}`),
    [query, sort, dir, current?.id],
  );
  const list = employees.data ?? [];

  const [exportError, setExportError] = useState<unknown>(null);
  const gdprExport = async (u: User) => {
    setExportError(null);
    try {
      const data = await api<unknown>(`/api/hr/employees/${u.id}/gdpr-export`);
      saveBlob(new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }), `rodo-art15-${u.id}.json`);
    } catch (e) {
      setExportError(e);
    }
  };

  const refreshAll = async () => {
    await Promise.all([employees.reload(), reloadUsers()]);
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-navy-900">Panel Kadr</h1>

      <Card title="Pracownicy urzędu">
        <div className="mb-4 grid gap-3 sm:grid-cols-3">
          <Field label="Szukaj (imię, nazwisko, e-mail)">
            <input type="search" className={inputClass} value={query} onChange={(e) => setQuery(e.target.value)} />
          </Field>
          <Field label="Sortuj według">
            <select className={inputClass} value={sort} onChange={(e) => setSort(e.target.value as SortKey)}>
              <option value="fullName">Imię i nazwisko</option>
              <option value="email">E-mail</option>
              <option value="role">Rola</option>
              <option value="id">ID</option>
            </select>
          </Field>
          <Field label="Kierunek">
            <select className={inputClass} value={dir} onChange={(e) => setDir(e.target.value as "ASC" | "DESC")}>
              <option value="ASC">rosnąco</option>
              <option value="DESC">malejąco</option>
            </select>
          </Field>
        </div>
        {employees.loading && <Spinner />}
        <ProblemAlert error={employees.error ?? exportError} />
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Pracownicy</caption>
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
              <tr>
                <th className="py-2 pr-3">Imię i nazwisko</th>
                <th className="py-2 pr-3">E-mail</th>
                <th className="py-2 pr-3">Rola</th>
                <th className="py-2 pr-3">Status SC</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody>
              {list.map((u) => (
                <tr key={u.id} className="border-b border-slate-100 last:border-0">
                  <td className="py-2 pr-3 font-medium">{u.fullName}</td>
                  <td className="py-2 pr-3">{u.email}</td>
                  <td className="py-2 pr-3">{ROLE_LABEL[u.role]}</td>
                  <td className="py-2 pr-3">{u.civilServant ? <Badge tone="blue">urzędnik SC</Badge> : <Badge>pracownik SC</Badge>}</td>
                  <td className="py-2 text-right">
                    <Button variant="secondary" onClick={() => gdprExport(u)}>
                      Eksport RODO (art. 15)
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-3">
        <AbsenceForm employees={list} />
        <HandoverForm employees={list} onDone={refreshAll} />
        <CsvImport onDone={refreshAll} />
      </div>
    </div>
  );
}

function AbsenceForm({ employees }: { employees: User[] }) {
  const [userId, setUserId] = useState("");
  const [start, setStart] = useState(today());
  const [end, setEnd] = useState(today());
  const [reason, setReason] = useState("Zwolnienie lekarskie");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [cycles, setCycles] = useState<Cycle[] | null>(null);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      setCycles(await api<Cycle[]>("/api/hr/absences", { body: { userId: Number(userId), startDate: start, endDate: end, reason } }));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Rejestracja nieobecności">
      <div className="space-y-3">
        <EmployeeSelect employees={employees} value={userId} onChange={setUserId} />
        <div className="grid grid-cols-2 gap-2">
          <Field label="Od">
            <input type="date" className={inputClass} value={start} onChange={(e) => setStart(e.target.value)} />
          </Field>
          <Field label="Do">
            <input type="date" className={inputClass} value={end} onChange={(e) => setEnd(e.target.value)} />
          </Field>
        </div>
        <Field label="Przyczyna">
          <input className={inputClass} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <p className="text-xs text-slate-600">Ciągła nieobecność dłuższa niż 1 miesiąc przedłuża termin oceny okresowej (art. 81 ust. 7).</p>
        <Button onClick={submit} busy={busy} disabled={!userId}>
          Zarejestruj i przelicz termin
        </Button>
        <ProblemAlert error={error} />
        {cycles && (
          <Alert tone="green" title="Przeliczone terminy aktywnych cykli">
            {cycles.length === 0 ? "Brak aktywnych cykli." : cycles.map((c) => <p key={c.id}>Cykl #{c.id}: nowy termin {formatDate(c.dueDate)}</p>)}
          </Alert>
        )}
      </div>
    </Card>
  );
}

function HandoverForm({ employees, onDone }: { employees: User[]; onDone: () => Promise<void> }) {
  const [employeeId, setEmployeeId] = useState("");
  const [evaluatorId, setEvaluatorId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [result, setResult] = useState<HandoverResult | null>(null);
  const evaluators = employees.filter((u) => u.role === "EVALUATOR" || u.role === "DG");

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      setResult(await api<HandoverResult>("/api/hr/handover", { body: { employeeId: Number(employeeId), newEvaluatorId: Number(evaluatorId) } }));
      await onDone();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Zmiana przełożonego (Handover)">
      <div className="space-y-3">
        <EmployeeSelect employees={employees} value={employeeId} onChange={setEmployeeId} />
        <Field label="Nowy oceniający">
          <select className={inputClass} value={evaluatorId} onChange={(e) => setEvaluatorId(e.target.value)}>
            <option value="">— wybierz —</option>
            {evaluators.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName} ({ROLE_LABEL[u.role]})
              </option>
            ))}
          </select>
        </Field>
        <p className="text-xs text-slate-600">§ 9 ust. 3: nowy przełożony przejmuje aktywny cykl i notatki, kryteria pozostają bez zmian.</p>
        <Button onClick={submit} busy={busy} disabled={!employeeId || !evaluatorId}>
          Przekaż cykl
        </Button>
        <ProblemAlert error={error} />
        {result && (
          <Alert tone="green" title="Przekazano">
            Cykle: {result.cycleIds.length ? result.cycleIds.map((id) => `#${id}`).join(", ") : "brak aktywnych"} · notatek: {result.notesTransferred}
          </Alert>
        )}
      </div>
    </Card>
  );
}

function CsvImport({ onDone }: { onDone: () => Promise<void> }) {
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  const submit = async () => {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.append("file", file);
      setResult(await api<ImportResult>("/api/hr/import-csv", { form }));
      await onDone();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Import pracowników (CSV)">
      <div className="space-y-3">
        <Field label="Plik CSV" hint="Kolumny: full_name,email,role,is_civil_servant[,supervisor_email]">
          <input type="file" accept=".csv,text/csv" className="block w-full text-sm" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        </Field>
        <Button onClick={submit} busy={busy} disabled={!file}>
          Importuj
        </Button>
        <ProblemAlert error={error} />
        {result && (
          <Alert tone={result.errors.length ? "amber" : "green"} title={`Zaimportowano: ${result.imported}`}>
            {[...result.skipped, ...result.errors].map((m) => (
              <p key={m}>{m}</p>
            ))}
          </Alert>
        )}
      </div>
    </Card>
  );
}

function EmployeeSelect({ employees, value, onChange }: { employees: User[]; value: string; onChange: (v: string) => void }) {
  return (
    <Field label="Pracownik">
      <select className={inputClass} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">— wybierz —</option>
        {employees.map((u) => (
          <option key={u.id} value={u.id}>
            {u.fullName}
          </option>
        ))}
      </select>
    </Field>
  );
}
