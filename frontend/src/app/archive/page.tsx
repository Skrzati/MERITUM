"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Alert, Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api, download } from "@/lib/api";
import { formatDate, OUTCOME_LABEL } from "@/lib/labels";
import type { Cycle, CycleStatus, Outcome } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

/** Zamknięte oceny (arkusz w części B akt osobowych) — w tym sporne przed sądem pracy (art. 83 ust. 5). */
const ARCHIVED: CycleStatus[] = ["COMPLETED", "APPEALED_TO_COURT"];
const ALL = "";

const evaluationDate = (c: Cycle) => c.evaluatedOn ?? c.acknowledgedAt;

export default function ArchivePage() {
  const { current, userName, userById } = useUser();
  const cycles = useAsync(async () => (current ? api<Cycle[]>("/api/cycles") : []), [current?.id]);
  const [year, setYear] = useState(ALL);
  const [department, setDepartment] = useState(ALL);
  const [outcome, setOutcome] = useState<Outcome | typeof ALL>(ALL);
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  const archived = useMemo(() => (cycles.data ?? []).filter((c) => ARCHIVED.includes(c.status)), [cycles.data]);
  const years = [...new Set(archived.map((c) => evaluationDate(c)?.slice(0, 4)).filter((y): y is string => !!y))].sort().reverse();
  const departments = [...new Set(archived.map((c) => userById(c.employeeId)?.department).filter((d): d is string => !!d))].sort();
  const needle = query.trim().toLowerCase();
  const visible = archived
    .filter((c) => year === ALL || evaluationDate(c)?.startsWith(year))
    .filter((c) => department === ALL || userById(c.employeeId)?.department === department)
    .filter((c) => outcome === ALL || c.finalResult === outcome)
    .filter((c) => !needle || userName(c.employeeId).toLowerCase().includes(needle))
    .sort((a, b) => (evaluationDate(b) ?? "").localeCompare(evaluationDate(a) ?? ""));

  const fetchFile = async (key: string, path: string, filename: string) => {
    setBusy(key);
    setError(null);
    try {
      await download(path, filename);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gov-text">Archiwum ocen</h1>
      <Alert tone="blue">
        Zakończone oceny okresowe widoczne dla Twojej roli. Arkusz oceny jest częścią B akt osobowych i nie jest usuwany
        automatycznie (10 lat albo 50 lat — zależnie od daty nawiązania stosunku pracy); spór przed sądem pracy blokuje brakowanie.
      </Alert>
      <Card title="Filtry">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Field label="Imię i nazwisko">
            <input type="search" className={inputClass} value={query} onChange={(e) => setQuery(e.target.value)} />
          </Field>
          <Field label="Rok oceny">
            <select className={inputClass} value={year} onChange={(e) => setYear(e.target.value)}>
              <option value={ALL}>Wszystkie</option>
              {years.map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Komórka organizacyjna">
            <select className={inputClass} value={department} onChange={(e) => setDepartment(e.target.value)}>
              <option value={ALL}>Wszystkie</option>
              {departments.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Wynik">
            <select className={inputClass} value={outcome} onChange={(e) => setOutcome(e.target.value as Outcome | typeof ALL)}>
              <option value={ALL}>Wszystkie</option>
              {(Object.keys(OUTCOME_LABEL) as Outcome[]).map((o) => (
                <option key={o} value={o}>
                  {OUTCOME_LABEL[o]}
                </option>
              ))}
            </select>
          </Field>
        </div>
      </Card>

      <Card title={`Oceny w archiwum · ${visible.length}`}>
        {cycles.loading && <Spinner />}
        <ProblemAlert error={cycles.error} />
        <ProblemAlert error={error} />
        {cycles.data && visible.length === 0 && <p className="text-sm text-slate-700">Brak ocen spełniających kryteria.</p>}
        {visible.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Archiwum ocen okresowych</caption>
              <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
                <tr>
                  <th className="py-2 pr-3">Oceniany</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 pr-3">Data i wynik</th>
                  <th className="py-2 pr-3">Retencja</th>
                  <th className="py-2 text-right">Dokumenty</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((c) => {
                  const employee = userById(c.employeeId);
                  return (
                    <tr key={c.id} className="border-b border-slate-100 last:border-0">
                      <td className="py-2 pr-3">
                        <span className="font-semibold text-gov-text">{userName(c.employeeId)}</span>
                        {employee?.department && <span className="block text-xs text-slate-600">{employee.department}</span>}
                      </td>
                      <td className="py-2 pr-3">{employee ? (employee.civilServant ? "Urzędnik SC" : "Pracownik SC") : "—"}</td>
                      <td className="py-2 pr-3">
                        <span className="block">{formatDate(evaluationDate(c))}</span>
                        {c.finalResult && (
                          <Badge tone={c.finalResult === "POSITIVE" ? "green" : "red"}>{OUTCOME_LABEL[c.finalResult]}</Badge>
                        )}
                      </td>
                      <td className="py-2 pr-3">
                        {c.legalHold ? (
                          <Badge tone="amber">Blokada sądowa — legal hold</Badge>
                        ) : (
                          <Badge tone="slate">Część B akt · 10/50 lat</Badge>
                        )}
                      </td>
                      <td className="py-2 text-right">
                        <div className="flex flex-wrap justify-end gap-1">
                          <Link
                            href={`/cycles/${c.id}`}
                            className="inline-flex min-h-[44px] items-center rounded px-5 py-2 font-bold text-gov-blue ring-1 ring-inset ring-gov-blue hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue focus-visible:ring-offset-2"
                          >
                            Podgląd
                          </Link>
                          <Button
                            variant="secondary"
                            busy={busy === `pdf-${c.id}`}
                            onClick={() => fetchFile(`pdf-${c.id}`, `/api/cycles/${c.id}/pdf`, `arkusz-oceny-${c.id}.pdf`)}
                          >
                            PDF
                          </Button>
                          <Button
                            variant="secondary"
                            busy={busy === `zip-${c.id}`}
                            onClick={() => fetchFile(`zip-${c.id}`, `/api/cycles/${c.id}/ezd-zip`, `ezd-paczka-cykl-${c.id}.zip`)}
                          >
                            Paczka EZD (ZIP)
                          </Button>
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
