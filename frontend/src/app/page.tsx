"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { AssignCycleDialog } from "@/components/AssignCycleDialog";
import { Badge, Button, Card, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import { dueState, formatDate, roleLabel, STATUS_LABEL, STATUS_TONE, today } from "@/lib/labels";
import type { Cycle, EmploymentStatus, Schema, Substitution, User } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

type CycleFilter = "ALL" | "MY_ACTION" | "URZEDNIK" | "PRACOWNIK";

const FILTERS: { id: CycleFilter; label: string }[] = [
  { id: "ALL", label: "Wszystkie" },
  { id: "MY_ACTION", label: "Wymagają mojej akcji" },
  { id: "URZEDNIK", label: "Urzędnicy SC" },
  { id: "PRACOWNIK", label: "Pracownicy SC" },
];

const EVALUATOR_ACTION: Cycle["status"][] = ["DRAFT_CRITERIA", "OBSERVATION", "EVALUATION_DRAFT"];

/** Uproszczona podpowiedź dla listy — o uprawnieniach i tak decyduje backend. */
function needsMyAction(c: Cycle, me: User) {
  if (c.evaluatorId === me.id) return EVALUATOR_ACTION.includes(c.status);
  if (c.employeeId === me.id) return c.status === "AWAITING_ACK";
  if (me.role === "DG") return c.status === "APPEALED";
  if (me.role === "HR") return c.overdueDays > 0;
  return false;
}

const isPracownik = (s?: EmploymentStatus) => s === "PRACOWNIK_SC_NIEOKRESLONY" || s === "PRACOWNIK_SC_OKRESLONY";

function EmploymentTag({ status }: { status?: EmploymentStatus }) {
  if (status === "URZEDNIK_SC") return <Badge tone="blue">Urzędnik SC</Badge>;
  if (isPracownik(status)) return <Badge>Pracownik SC</Badge>;
  if (status === "WYZSZE_STANOWISKO_SC") return <Badge tone="amber">Wyższe stanowisko SC</Badge>;
  return null;
}

export default function DashboardPage() {
  const router = useRouter();
  const { current, users, userName, userById, loading: userLoading, error: userError } = useUser();
  const cycles = useAsync(async () => (current ? api<Cycle[]>("/api/cycles") : []), [current?.id]);
  const schemaIds = useMemo(() => [...new Set((cycles.data ?? []).map((c) => c.schemaVersionId))], [cycles.data]);
  const schemas = useAsync(
    async () => Promise.all(schemaIds.map((id) => api<Schema>(`/api/schemas/${id}`))),
    [schemaIds.join(","), current?.id],
  );
  const versionOf = (id: number) => schemas.data?.find((s) => s.id === id)?.versionCode ?? `#${id}`;
  const [creating, setCreating] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);
  const [filter, setFilter] = useState<CycleFilter>("ALL");

  // Kadry i kierownictwo urzędu — wszyscy pracownicy; kierownik — bezpośredni podwładni.
  const canCreate =
    !!current && (current.role === "HR" || current.role === "DG" || users.some((u) => u.supervisorId === current.id));
  const substitutions = useAsync(
    async () => (current?.role === "EVALUATOR" || current?.role === "DG" ? api<Substitution[]>("/api/substitutions") : []),
    [current?.id],
  );
  const mySubstitutions = (substitutions.data ?? []).filter((s) => s.activeToday || s.startDate > today());

  const startNext = async (cycleId: number) => {
    setBusyId(cycleId);
    setActionError(null);
    try {
      const next = await api<Cycle>(`/api/cycles/${cycleId}/next`, { method: "POST" });
      router.push(`/cycles/${next.id}`);
    } catch (e) {
      setActionError(e);
      setBusyId(null);
    }
  };

  if (userLoading) return <Spinner />;
  if (userError) return <ProblemAlert error={`Brak połączenia z backend-core: ${userError}`} />;

  const now = today();
  const statusOf = (c: Cycle) => userById(c.employeeId)?.employmentStatus;
  const matches = (f: CycleFilter) => (c: Cycle) =>
    f === "ALL" ||
    (f === "MY_ACTION" && !!current && needsMyAction(c, current)) ||
    (f === "URZEDNIK" && statusOf(c) === "URZEDNIK_SC") ||
    (f === "PRACOWNIK" && isPracownik(statusOf(c)));
  const visible = (cycles.data ?? []).filter(matches(filter));
  const mayStartNext = (c: Cycle) => c.status === "COMPLETED" && (current?.role === "HR" || current?.id === c.evaluatorId);

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-navy-900">Pulpit</h1>
          <p className="text-sm text-slate-700">
            Cykle ocen widoczne dla roli <strong>{roleLabel(current)}</strong>
            {current?.officeName ? ` · ${current.officeName}` : ""}.
          </p>
        </div>
        {canCreate && <Button onClick={() => setCreating(true)}>+ Przypisz nową ocenę (utwórz cykl)</Button>}
      </div>

      {creating && (
        <AssignCycleDialog
          onClose={() => setCreating(false)}
          onCreated={(created, mode) => (mode === "single" ? router.push(`/cycles/${created[0].id}`) : void cycles.reload())}
        />
      )}

      {mySubstitutions.length > 0 && current && (
        <Card title="Zastępstwa">
          <ul className="space-y-2 text-sm">
            {mySubstitutions.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center gap-2">
                <Badge tone={s.activeToday ? "green" : "slate"}>{s.activeToday ? "trwa" : "zaplanowane"}</Badge>
                {s.deputyId === current.id
                  ? `Zastępujesz: ${userName(s.evaluatorId)} — prowadzisz aktywne cykle tej osoby (oceny, notatki, rozmowy)`
                  : `Twój zastępca: ${userName(s.deputyId)}`}
                <span className="text-slate-600">
                  ({formatDate(s.startDate)} – {formatDate(s.endDate)}; {s.reason})
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card title="Cykle oceny okresowej">
        {cycles.loading && <Spinner />}
        <ProblemAlert error={cycles.error} />
        <ProblemAlert error={actionError} />
        {cycles.data && cycles.data.length === 0 && <p className="text-sm text-slate-700">Brak cykli do wyświetlenia.</p>}
        {cycles.data && cycles.data.length > 0 && (
          <div role="group" aria-label="Filtr listy ocen" className="mb-5 flex flex-wrap gap-2">
            {FILTERS.map((f) => (
              <button
                key={f.id}
                type="button"
                aria-pressed={filter === f.id}
                onClick={() => setFilter(f.id)}
                className={`min-h-[44px] rounded px-4 text-base font-bold focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue focus-visible:ring-offset-2 ${
                  filter === f.id ? "bg-gov-blue text-white" : "bg-white text-gov-blue ring-1 ring-inset ring-gov-blue hover:bg-gov-light"
                }`}
              >
                {f.label} ({(cycles.data ?? []).filter(matches(f.id)).length})
              </button>
            ))}
          </div>
        )}
        {cycles.data && cycles.data.length > 0 && visible.length === 0 && (
          <p className="text-base text-slate-700">Brak ocen spełniających wybrany filtr.</p>
        )}
        {visible.length > 0 && (
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
                {visible.map((c) => {
                  const due = dueState(c, now);
                  return (
                    <tr key={c.id} className="border-b border-slate-100 align-top last:border-0">
                      <td className="py-3 pr-4 font-medium">
                        {userName(c.employeeId)}
                        {c.employeeId === current?.id && <span className="ml-2 text-xs text-slate-600">(Ty)</span>}
                        <div className="mt-1 flex flex-wrap gap-1">
                          <EmploymentTag status={statusOf(c)} />
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
                            className="inline-flex min-h-[44px] items-center rounded bg-gov-blue px-5 text-base font-bold text-white hover:bg-gov-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue focus-visible:ring-offset-2"
                          >
                            Otwórz ocenę
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
