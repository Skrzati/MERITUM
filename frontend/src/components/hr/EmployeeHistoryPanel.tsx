"use client";

import { useState } from "react";
import { Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate, LEVEL_LABEL, OUTCOME_LABEL, today } from "@/lib/labels";
import type { HistoryEntry, Outcome, PerformanceLevel, User } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const SOURCE_LABEL: Record<HistoryEntry["source"], string> = { SYSTEM: "z systemu", HISTORICAL: "sprzed wdrożenia (kadry)" };

/** Historia ocen pracownika — wgląd oraz dopisanie ocen sprzed wdrożenia (source = HISTORICAL). */
export function EmployeeHistoryPanel({ employee, onClose }: { employee: User; onClose: () => void }) {
  const history = useAsync(() => api<HistoryEntry[]>(`/api/hr/employees/${employee.id}/history`), [employee.id]);
  const [outcome, setOutcome] = useState<Outcome>("POSITIVE");
  const [level, setLevel] = useState<PerformanceLevel>("MEETS");
  const [date, setDate] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const add = async () => {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/hr/employees/${employee.id}/history`, { body: { outcome, level, evaluatedOn: date } });
      setDate("");
      await history.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card
      title={`Historia ocen: ${employee.fullName}`}
      actions={
        <Button variant="secondary" onClick={onClose}>
          Zamknij
        </Button>
      }
    >
      {history.loading && !history.data && <Spinner />}
      <ProblemAlert error={history.error} />
      <HistoryTable entries={history.data ?? []} />
      <div className="mt-4 grid gap-3 border-t border-slate-200 pt-4 sm:grid-cols-4">
        <Field label="Ocena">
          <select className={inputClass} value={outcome} onChange={(e) => setOutcome(e.target.value as Outcome)}>
            {(Object.keys(OUTCOME_LABEL) as Outcome[]).map((o) => (
              <option key={o} value={o}>
                {OUTCOME_LABEL[o]}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Poziom">
          <select className={inputClass} value={level} onChange={(e) => setLevel(e.target.value as PerformanceLevel)}>
            {(Object.keys(LEVEL_LABEL) as PerformanceLevel[]).map((l) => (
              <option key={l} value={l}>
                {LEVEL_LABEL[l]}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Data sporządzenia">
          <input type="date" max={today()} className={inputClass} value={date} onChange={(e) => setDate(e.target.value)} />
        </Field>
        <div className="flex items-end">
          <Button onClick={add} busy={busy} disabled={!date}>
            Dopisz ocenę historyczną
          </Button>
        </div>
      </div>
      <div className="mt-2">
        <ProblemAlert error={error} />
      </div>
    </Card>
  );
}

export function HistoryTable({ entries }: { entries: HistoryEntry[] }) {
  if (entries.length === 0) return <p className="text-sm text-slate-700">Brak ocen w historii.</p>;
  return (
    <table className="w-full text-left text-sm">
      <caption className="sr-only">Historia ocen</caption>
      <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
        <tr>
          <th className="py-2 pr-3">Data</th>
          <th className="py-2 pr-3">Ocena</th>
          <th className="py-2 pr-3">Poziom</th>
          <th className="py-2">Źródło</th>
        </tr>
      </thead>
      <tbody>
        {[...entries].reverse().map((h) => (
          <tr key={h.id} className="border-b border-slate-100 last:border-0">
            <td className="py-2 pr-3">{formatDate(h.evaluatedOn)}</td>
            <td className="py-2 pr-3">
              <Badge tone={h.outcome === "POSITIVE" ? "green" : "red"}>{OUTCOME_LABEL[h.outcome]}</Badge>
            </td>
            <td className="py-2 pr-3">{LEVEL_LABEL[h.level]}</td>
            <td className="py-2">
              {SOURCE_LABEL[h.source]}
              {h.cycleId ? ` · cykl #${h.cycleId}` : ""}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
