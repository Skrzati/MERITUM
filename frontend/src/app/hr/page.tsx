"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { RoleGate } from "@/components/RoleGate";
import { Alert, Badge, Button, Card, CollapsibleSection, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api, download, saveBlob } from "@/lib/api";
import { AssignCycleDialog } from "@/components/AssignCycleDialog";
import { EmployeeEditDialog } from "@/components/hr/EmployeeEditDialog";
import { EmployeeHistoryPanel } from "@/components/hr/EmployeeHistoryPanel";
import { HrAlertsCard } from "@/components/hr/HrAlertsCard";
import { IprzSummaryCard } from "@/components/hr/IprzSummaryCard";
import { RetentionCard } from "@/components/hr/RetentionCard";
import { SubstitutionsCard } from "@/components/hr/SubstitutionsCard";
import { EMPLOYMENT_STATUS_LABEL, escalationLabel, formatDate, roleLabel, today, type Tone } from "@/lib/labels";
import type { Cycle, EmploymentStatus, EscalationLevel, HandoverResult, ImportResult, OverdueItem, RemovalOutcome, User } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

type SortKey = "fullName" | "email" | "role" | "id";

/** Drabina eskalacji (OverdueRules): 1–7 dni oceniający, 8–14 zastępca, 15–30 kadry, > 30 DG / kierownik urzędu. */
const ESCALATION_TONE: Record<EscalationLevel, Tone> = {
  OCENIAJACY: "blue",
  ZASTEPCA: "amber",
  KADRY: "amber",
  DYREKTOR_GENERALNY: "red",
};

export default function HrPage() {
  return (
    <RoleGate roles={["HR"]}>
      <HrPanel />
    </RoleGate>
  );
}

type StatusGroup = "ALL" | "URZEDNIK" | "NIEOKRESLONY" | "OUTSIDE";

const STATUS_GROUPS: { id: StatusGroup; label: string; statuses?: EmploymentStatus[] }[] = [
  { id: "ALL", label: "Wszyscy" },
  { id: "URZEDNIK", label: "Urzędnicy SC", statuses: ["URZEDNIK_SC"] },
  { id: "NIEOKRESLONY", label: "Pracownicy SC (czas nieokreślony)", statuses: ["PRACOWNIK_SC_NIEOKRESLONY"] },
  {
    id: "OUTSIDE",
    label: "Poza oceną okresową (czas określony / wyższe stanowiska)",
    statuses: ["PRACOWNIK_SC_OKRESLONY", "WYZSZE_STANOWISKO_SC"],
  },
];

const inGroup = (u: User, group: (typeof STATUS_GROUPS)[number]) => !group.statuses || group.statuses.includes(u.employmentStatus);

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
  const [group, setGroup] = useState<StatusGroup>("ALL");
  const activeGroup = STATUS_GROUPS.find((g) => g.id === group) ?? STATUS_GROUPS[0];
  const visible = list.filter((u) => inGroup(u, activeGroup));

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

  const overdue = useAsync(() => api<OverdueItem[]>("/api/hr/overdue"), [current?.id]);
  const [handoverEmployeeId, setHandoverEmployeeId] = useState("");
  const handoverRef = useRef<HTMLDivElement>(null);
  const [reportBusy, setReportBusy] = useState(false);

  const refreshAll = async () => {
    await Promise.all([employees.reload(), reloadUsers(), overdue.reload()]);
  };

  const [assigning, setAssigning] = useState(false);
  const [assigned, setAssigned] = useState<number | null>(null);
  const [editing, setEditing] = useState<User | null>(null);
  const [historyOf, setHistoryOf] = useState<User | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<number | null>(null);
  const [removed, setRemoved] = useState<{ name: string; outcome: RemovalOutcome } | null>(null);
  const roleOf = (u: User) => roleLabel({ role: u.role, officeHasDgRole: current?.officeHasDgRole });

  const removeEmployee = async (u: User) => {
    setExportError(null);
    setRemoved(null);
    try {
      const res = await api<{ outcome: RemovalOutcome }>(`/api/hr/employees/${u.id}`, { method: "DELETE" });
      setRemoved({ name: u.fullName, outcome: res.outcome });
      setConfirmRemove(null);
      if (historyOf?.id === u.id) setHistoryOf(null);
      await refreshAll();
    } catch (e) {
      setExportError(e);
      setConfirmRemove(null);
    }
  };

  const changeEvaluator = (employeeId: number) => {
    setHandoverEmployeeId(String(employeeId));
    handoverRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    handoverRef.current?.querySelector<HTMLSelectElement>("select[data-new-evaluator]")?.focus({ preventScroll: true });
  };

  const downloadReport = (path: string, filename: string) => async () => {
    setReportBusy(true);
    setExportError(null);
    try {
      await download(path, filename);
    } catch (e) {
      setExportError(e);
    } finally {
      setReportBusy(false);
    }
  };

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gov-text">Panel Kadr</h1>
          <p className="text-sm text-slate-700">Raport sprawozdawczy jest zanonimizowany (k-anonimizacja po stronie serwera, UTF-8 dla Excela).</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => setAssigning(true)}>+ Przypisz nową ocenę</Button>
          <Button variant="secondary" onClick={downloadReport("/api/hr/reports/export", `raport-ocen-${today()}.csv`)} busy={reportBusy}>
            ⬇ Pobierz raport sprawozdawczy (CSV)
          </Button>
          <Button
            variant="secondary"
            onClick={downloadReport(
              `/api/hr/export/ssc-report.csv?year=${new Date().getFullYear()}`,
              `sprawozdanie-szefa-sc-${new Date().getFullYear()}.csv`,
            )}
            busy={reportBusy}
          >
            ⬇ Eksport do Sprawozdania Szefa SC (CSV)
          </Button>
        </div>
      </div>
      {assigning && (
        <AssignCycleDialog
          onClose={() => setAssigning(false)}
          onCreated={(created, mode) => {
            setAssigned(created.length);
            if (mode === "single") setAssigning(false);
            void overdue.reload();
          }}
        />
      )}
      {assigned !== null && <Alert tone="green">Utworzono cykli oceny: {assigned}. Znajdziesz je na Pulpicie.</Alert>}

      <HrAlertsCard reloadKey={current?.id} />
      <CollapsibleSection title="Plan szkoleń z IPRZ" hint="Zestawienie działań rozwojowych z zatwierdzonych programów (art. 108)">
        <IprzSummaryCard reloadKey={current?.id} />
      </CollapsibleSection>
      <CollapsibleSection title="Retencja i dane pomocnicze" hint="Czyszczenie notatek roboczych i ankiet zamkniętych cykli">
        <RetentionCard />
      </CollapsibleSection>

      <Card title="Zaległości i drabina eskalacji">
        <p className="mb-3 text-sm text-slate-700">
          Ocena po terminie nie jest blokowana — system liczy dni opóźnienia i wskazuje szczebel eskalacji.
        </p>
        {overdue.loading && <Spinner />}
        <ProblemAlert error={overdue.error} />
        {overdue.data && overdue.data.length === 0 && <Alert tone="green">Brak cykli po terminie.</Alert>}
        {overdue.data && overdue.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Cykle po terminie</caption>
              <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
                <tr>
                  <th className="py-2 pr-3">Oceniany</th>
                  <th className="py-2 pr-3">Oceniający</th>
                  <th className="py-2 pr-3">Termin</th>
                  <th className="py-2 pr-3">Dni po terminie</th>
                  <th className="py-2 pr-3">Powód opóźnienia</th>
                  <th className="py-2 pr-3">Szczebel eskalacji</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {overdue.data.map((o) => (
                  <tr key={o.cycleId} className="border-b border-slate-100 align-top last:border-0">
                    <td className="py-2 pr-3 font-medium">
                      <Link href={`/cycles/${o.cycleId}`} className="text-gov-blue underline">
                        {o.employeeName ?? `#${o.employeeId}`}
                      </Link>
                    </td>
                    <td className="py-2 pr-3">{o.evaluatorName ?? `#${o.evaluatorId}`}</td>
                    <td className="py-2 pr-3">{formatDate(o.dueDate)}</td>
                    <td className="py-2 pr-3 font-bold text-red-800">{o.overdueDays}</td>
                    <td className="py-2 pr-3">{o.delayReason ?? <span className="text-slate-500">nie podano</span>}</td>
                    <td className="py-2 pr-3">
                      <Badge tone={ESCALATION_TONE[o.escalationLevel]}>
                        {escalationLabel(o.escalationLevel, current?.officeHasDgRole !== false)}
                      </Badge>
                    </td>
                    <td className="py-2 text-right">
                      <Button variant="secondary" onClick={() => changeEvaluator(o.employeeId)}>
                        Zmień oceniającego
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

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
        <div role="group" aria-label="Filtr według statusu zatrudnienia" className="mb-4 flex flex-wrap gap-2">
          {STATUS_GROUPS.map((g) => (
            <button
              key={g.id}
              type="button"
              aria-pressed={group === g.id}
              onClick={() => setGroup(g.id)}
              className={`min-h-[44px] rounded px-4 text-base font-bold focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue focus-visible:ring-offset-2 ${
                group === g.id ? "bg-gov-blue text-white" : "bg-white text-gov-blue ring-1 ring-inset ring-gov-blue hover:bg-gov-light"
              }`}
            >
              {g.label} ({list.filter((u) => inGroup(u, g)).length})
            </button>
          ))}
        </div>
        {employees.loading && <Spinner />}
        <ProblemAlert error={employees.error ?? exportError} />
        {removed && (
          <Alert tone="green" title={removed.outcome === "DELETED" ? "Dane usunięte" : "Dane zanonimizowane"}>
            {removed.outcome === "DELETED"
              ? `Konto ${removed.name} nie miało śladów w procesie i zostało usunięte.`
              : `Konto ${removed.name} ma cykle/historię — dane osobowe i notatki robocze usunięto, arkusze i audyt zachowano.`}
          </Alert>
        )}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Pracownicy</caption>
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
              <tr>
                <th className="py-2 pr-3">Imię i nazwisko</th>
                <th className="py-2 pr-3">E-mail</th>
                <th className="py-2 pr-3">Rola</th>
                <th className="py-2 pr-3">Komórka / stanowisko</th>
                <th className="py-2 pr-3">Status SC</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody>
              {visible.map((u) => (
                <tr key={u.id} className="border-b border-slate-100 last:border-0">
                  <td className="py-2 pr-3 font-medium">{u.fullName}</td>
                  <td className="py-2 pr-3">{u.email}</td>
                  <td className="py-2 pr-3">{roleOf(u)}</td>
                  <td className="py-2 pr-3 text-xs">
                    {u.department ?? "—"}
                    {u.positionTitle ? <span className="block text-slate-600">{u.positionTitle}</span> : null}
                  </td>
                  <td className="py-2 pr-3">
                    <Badge tone={u.civilServant ? "blue" : u.employmentStatus === "PRACOWNIK_SC_NIEOKRESLONY" ? "slate" : "amber"}>
                      {EMPLOYMENT_STATUS_LABEL[u.employmentStatus]}
                      {u.civilServant && u.civilServiceGrade ? ` · st. ${u.civilServiceGrade}` : ""}
                    </Badge>
                  </td>
                  <td className="py-2 text-right">
                    <div className="flex flex-wrap justify-end gap-1">
                      <Button variant="secondary" onClick={() => setEditing(u)}>
                        Edytuj
                      </Button>
                      <Button variant="secondary" onClick={() => setHistoryOf(u)}>
                        Historia ocen
                      </Button>
                      <Button variant="secondary" onClick={() => gdprExport(u)}>
                        RODO art. 15
                      </Button>
                      {u.id !== current?.id &&
                        (confirmRemove === u.id ? (
                          <>
                            <Button variant="danger" onClick={() => removeEmployee(u)}>
                              Potwierdź usunięcie
                            </Button>
                            <Button variant="secondary" onClick={() => setConfirmRemove(null)}>
                              Anuluj
                            </Button>
                          </>
                        ) : (
                          <Button variant="secondary" onClick={() => setConfirmRemove(u.id)}>
                            Usuń / anonimizuj
                          </Button>
                        ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {historyOf && <EmployeeHistoryPanel employee={historyOf} onClose={() => setHistoryOf(null)} />}
      {editing && <EmployeeEditDialog employee={editing} employees={list} onClose={() => setEditing(null)} onSaved={refreshAll} />}

      <CollapsibleSection title="Zastępstwa oceniających" hint="Kto prowadzi oceny pod nieobecność przełożonego">
        <SubstitutionsCard employees={list} />
      </CollapsibleSection>

      <div className="grid gap-6 lg:grid-cols-3">
        <AbsenceForm employees={list} />
        <div ref={handoverRef} id="handover">
          <HandoverForm employees={list} employeeId={handoverEmployeeId} onEmployeeChange={setHandoverEmployeeId} onDone={refreshAll} />
        </div>
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

function HandoverForm({
  employees,
  employeeId,
  onEmployeeChange,
  onDone,
}: {
  employees: User[];
  employeeId: string;
  onEmployeeChange: (id: string) => void;
  onDone: () => Promise<void>;
}) {
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
        <EmployeeSelect employees={employees} value={employeeId} onChange={onEmployeeChange} />
        <Field label="Nowy oceniający">
          <select data-new-evaluator className={inputClass} value={evaluatorId} onChange={(e) => setEvaluatorId(e.target.value)}>
            <option value="">— wybierz —</option>
            {evaluators.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName}
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
        <Field label="Plik CSV" hint="Kolumny: full_name,email,role,is_civil_servant|employment_status[,supervisor_email]">
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
