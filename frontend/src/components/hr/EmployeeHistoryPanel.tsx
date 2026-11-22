"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Badge, Button, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { api, download } from "@/lib/api";
import { formatDate, LEVEL_LABEL, OUTCOME_LABEL, today } from "@/lib/labels";
import type { HistoryEntry, Outcome, PerformanceLevel, User } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const SOURCE_LABEL: Record<HistoryEntry["source"], string> = { SYSTEM: "z systemu", HISTORICAL: "sprzed wdrożenia (kadry)" };

/** Historia ocen pracownika w oknie modalnym (Escape zamyka) — wgląd oraz dopisanie ocen sprzed wdrożenia (HISTORICAL). */
export function EmployeeHistoryPanel({ employee, onClose }: { employee: User; onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const history = useAsync(() => api<HistoryEntry[]>(`/api/hr/employees/${employee.id}/history`), [employee.id]);
  const [outcome, setOutcome] = useState<Outcome>("POSITIVE");
  const [level, setLevel] = useState<PerformanceLevel>("MEETS");
  const [date, setDate] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);

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
    <dialog ref={dialogRef} onClose={onClose} aria-labelledby="history-title" className="w-full max-w-3xl rounded p-0 shadow-xl backdrop:bg-slate-900/50">
      <div aria-hidden className="h-1.5 bg-gov-blue" />
      <div className="space-y-4 p-6">
        <div className="flex items-start justify-between gap-3">
          <h2 id="history-title" className="text-lg font-bold text-gov-text">
            Historia ocen: {employee.fullName}
          </h2>
          <Button variant="secondary" onClick={onClose}>
            Zamknij
          </Button>
        </div>
        {history.loading && !history.data && <Spinner />}
        <ProblemAlert error={history.error} />
        <HistoryTable entries={history.data ?? []} withCycleLinks />
        <div className="grid gap-3 border-t border-slate-200 pt-4 sm:grid-cols-4">
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
        <ProblemAlert error={error} />
      </div>
    </dialog>
  );
}

/** `withCycleLinks` — przy ocenach z systemu przyciski „Otwórz arkusz” i „Pobierz PDF”. */
export function HistoryTable({ entries, withCycleLinks = false }: { entries: HistoryEntry[]; withCycleLinks?: boolean }) {
  const [pdfBusy, setPdfBusy] = useState<number | null>(null);
  const [pdfError, setPdfError] = useState<unknown>(null);
  if (entries.length === 0) return <p className="text-sm text-slate-700">Brak ocen w historii.</p>;

  const pdf = async (cycleId: number) => {
    setPdfBusy(cycleId);
    setPdfError(null);
    try {
      await download(`/api/cycles/${cycleId}/pdf`, `arkusz-oceny-${cycleId}.pdf`);
    } catch (e) {
      setPdfError(e);
    } finally {
      setPdfBusy(null);
    }
  };

  return (
    <>
      <ProblemAlert error={pdfError} />
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Historia ocen</caption>
        <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
          <tr>
            <th className="py-2 pr-3">Data</th>
            <th className="py-2 pr-3">Ocena</th>
            <th className="py-2 pr-3">Poziom</th>
            <th className="py-2">Źródło</th>
            {withCycleLinks && <th className="py-2 text-right">Arkusz</th>}
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
              {withCycleLinks && (
                <td className="py-2 text-right">
                  {h.source === "SYSTEM" && h.cycleId && (
                    <div className="flex flex-wrap justify-end gap-1">
                      <Link
                        href={`/cycles/${h.cycleId}`}
                        className="inline-flex min-h-[44px] items-center rounded px-5 py-2 font-bold text-gov-blue ring-1 ring-inset ring-gov-blue hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue focus-visible:ring-offset-2"
                      >
                        Otwórz arkusz
                      </Link>
                      <Button variant="secondary" busy={pdfBusy === h.cycleId} onClick={() => pdf(h.cycleId!)}>
                        Pobierz PDF
                      </Button>
                    </div>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
