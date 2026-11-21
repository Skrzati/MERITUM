"use client";

import { RoleGate } from "@/components/RoleGate";
import { Alert, Card, ProblemAlert, Spinner, Stat } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import { LEVEL_LABEL, OUTCOME_LABEL, STATUS_LABEL } from "@/lib/labels";
import type { StatsSummary } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

/** Próg anonimizacji: liczności 1..K-1 nie są pokazywane, by nie dało się wskazać konkretnej osoby. */
const K_ANONYMITY = 3;

function masked(n: number): string {
  return n > 0 && n < K_ANONYMITY ? `<${K_ANONYMITY}` : String(n);
}

export default function StatsPage() {
  return (
    <RoleGate roles={["DG", "HR"]}>
      <Stats />
    </RoleGate>
  );
}

function Stats() {
  const { current } = useUser();
  const stats = useAsync(() => api<StatsSummary>("/api/stats/summary"), [current?.id]);
  const s = stats.data;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-navy-900">Statystyki urzędu</h1>
      <Alert tone="blue" title={`Próg anonimizacji k = ${K_ANONYMITY}`}>
        Prezentowane są wyłącznie dane zagregowane. Liczności mniejsze niż {K_ANONYMITY} (poza zerem) są ukrywane jako „&lt;{K_ANONYMITY}”,
        aby nie dało się zidentyfikować pojedynczych osób.
      </Alert>
      {stats.loading && <Spinner />}
      <ProblemAlert error={stats.error} />
      {s && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            <Stat label="Cykle ogółem" value={s.totalCycles} />
            <Stat label="Zaległe" value={masked(s.overdue)} tone={s.overdue ? "red" : "slate"} hint="po terminie, nieukończone" />
            <Stat label="Termin ≤ 7 dni" value={masked(s.dueIn7Days)} tone="amber" />
            <Stat label="Termin ≤ 30 dni" value={masked(s.dueIn30Days)} tone="amber" />
            <Stat label="Sprzeciwy" value={masked(s.appeals)} tone="blue" />
          </div>
          <div className="grid gap-6 lg:grid-cols-3">
            <Distribution title="Rozkład ocen" data={s.byOutcome} label={(k) => OUTCOME_LABEL[k as keyof typeof OUTCOME_LABEL] ?? k} />
            <Distribution title="Poziomy wykonywania obowiązków" data={s.byLevel} label={(k) => LEVEL_LABEL[k as keyof typeof LEVEL_LABEL] ?? k} />
            <Distribution title="Etapy procesu" data={s.byStatus} label={(k) => STATUS_LABEL[k as keyof typeof STATUS_LABEL] ?? k} />
          </div>
        </>
      )}
    </div>
  );
}

function Distribution({ title, data, label }: { title: string; data: Record<string, number>; label: (k: string) => string }) {
  const entries = Object.entries(data);
  const total = entries.reduce((a, [, v]) => a + v, 0);
  return (
    <Card title={title}>
      {entries.length === 0 ? (
        <p className="text-sm text-slate-700">Brak danych.</p>
      ) : (
        <ul className="space-y-3">
          {entries.map(([k, v]) => {
            const hidden = v > 0 && v < K_ANONYMITY;
            return (
              <li key={k}>
                <div className="flex justify-between text-sm">
                  <span>{label(k)}</span>
                  <strong>{masked(v)}</strong>
                </div>
                <div className="mt-1 h-2 rounded bg-slate-100" aria-hidden>
                  {!hidden && <div className="h-2 rounded bg-navy-600" style={{ width: `${(v / total) * 100}%` }} />}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
