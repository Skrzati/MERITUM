"use client";

import { useState } from "react";
import { Alert, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { LEVEL_LABEL } from "@/lib/labels";
import type { AnonymizedCount, Calibration, PerformanceLevel } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const LEVELS = Object.keys(LEVEL_LABEL) as PerformanceLevel[];
const YEARS_BACK = 3;

function show(c: AnonymizedCount | undefined, k: number): string {
  if (!c) return "0";
  return c.suppressed ? `<${k}` : String(c.value ?? 0);
}

function decimal(value: number | null): string {
  return value === null ? "—" : value.toFixed(1).replace(".", ",");
}

/** Kalibracja ocen per komórka (GET /api/stats/calibration) — komórki z < k ocenami backend pomija w całości. */
export function CalibrationCard({ reloadKey }: { reloadKey?: unknown }) {
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState("");
  const calibration = useAsync(() => api<Calibration>(`/api/stats/calibration${year ? `?year=${year}` : ""}`), [reloadKey, year]);
  const c = calibration.data;

  return (
    <Card
      title={`Kalibracja ocen wg komórek (k-anonimizacja ≥ ${c?.kAnonymity ?? 5})`}
      actions={
        <div className="w-44">
          <Field label="Rok">
            <select className={inputClass} value={year} onChange={(e) => setYear(e.target.value)}>
              <option value="">wszystkie lata</option>
              {Array.from({ length: YEARS_BACK + 1 }, (_, i) => currentYear - i).map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </select>
          </Field>
        </div>
      }
    >
      {calibration.loading && !c && <Spinner />}
      <ProblemAlert error={calibration.error} />
      {c && (
        <div className="space-y-3">
          {c.departments.length === 0 ? (
            <Alert tone="slate">Żadna komórka nie ma co najmniej {c.kAnonymity} ocen — dane pominięto dla ochrony prywatności.</Alert>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <caption className="sr-only">Kalibracja ocen według komórek organizacyjnych</caption>
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs text-slate-600">
                    <th className="py-2 pr-3">Komórka</th>
                    <th className="py-2 pr-3 text-right">Oceny</th>
                    <th className="py-2 pr-3 text-right">Średnia</th>
                    <th className="py-2 pr-3 text-right">Pozytywne</th>
                    <th className="py-2 pr-3 text-right">Negatywne</th>
                    {LEVELS.map((l) => (
                      <th key={l} className="py-2 pr-3 text-right font-medium">
                        {LEVEL_LABEL[l]}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {c.departments.map((d) => (
                    <tr key={d.department} className="border-b border-slate-100 last:border-0">
                      <th scope="row" className="py-2 pr-3 text-left font-medium">
                        {d.department}
                      </th>
                      <td className="py-2 pr-3 text-right">{d.evaluations}</td>
                      <td className="py-2 pr-3 text-right font-semibold">{decimal(d.averageScore)}</td>
                      <td className="py-2 pr-3 text-right">{show(d.positive, c.kAnonymity)}</td>
                      <td className="py-2 pr-3 text-right">{show(d.negative, c.kAnonymity)}</td>
                      {LEVELS.map((l) => (
                        <td key={l} className="py-2 pr-3 text-right">
                          {show(d.levels[l], c.kAnonymity)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="text-xs text-slate-600">
            Średnia urzędu: <strong>{decimal(c.officeAverage)}</strong>
            {c.suppressedDepartments > 0 ? ` · pominięte komórki (mniej niż ${c.kAnonymity} ocen): ${c.suppressedDepartments}` : ""}. Liczności
            od 1 do {c.kAnonymity - 1} są ukryte jako „&lt;{c.kAnonymity}”.
          </p>
        </div>
      )}
    </Card>
  );
}
