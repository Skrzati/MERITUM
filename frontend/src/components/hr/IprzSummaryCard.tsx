"use client";

import { Alert, Card, ProblemAlert, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import type { IprzFormType, IprzSummary } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const FORM_LABEL: Record<IprzFormType, string> = {
  SZKOLENIE: "Szkolenia",
  MENTORING: "Mentoring",
  SAMOKSZTALCENIE: "Samokształcenie",
  PROJEKT: "Projekty",
  INNE: "Inne",
};

/** Zbiorczy plan szkoleń z zatwierdzonych IPRZ (art. 108) — GET /api/hr/iprz-summary. */
export function IprzSummaryCard({ reloadKey }: { reloadKey?: unknown }) {
  const summary = useAsync(() => api<IprzSummary>("/api/hr/iprz-summary"), [reloadKey]);
  const s = summary.data;
  return (
    <Card title="Zbiorczy plan szkoleń (IPRZ)">
      {summary.loading && !s && <Spinner />}
      <ProblemAlert error={summary.error} />
      {s && s.approvedPlans === 0 && <Alert tone="slate">Brak zatwierdzonych IPRZ — plan szkoleń powstanie po ich zatwierdzeniu.</Alert>}
      {s && s.approvedPlans > 0 && (
        <div className="space-y-4 text-sm">
          <p className="text-slate-700">
            Zatwierdzone plany: <strong>{s.approvedPlans}</strong>. Obszary rozwojowe od najczęstszych.
          </p>
          <div className="overflow-x-auto">
            <table className="w-full">
              <caption className="sr-only">Obszary rozwojowe z zatwierdzonych IPRZ</caption>
              <thead>
                <tr className="border-b border-slate-200 text-left text-xs text-slate-600">
                  <th className="py-2 pr-3">Kryterium</th>
                  <th className="py-2 pr-3 text-right">Działania</th>
                  <th className="py-2 text-right">Osoby</th>
                </tr>
              </thead>
              <tbody>
                {s.byCriterion.map((c) => (
                  <tr key={c.criterionCode} className="border-b border-slate-100 last:border-0">
                    <th scope="row" className="py-2 pr-3 text-left font-medium">
                      {c.criterionName} <span className="font-mono text-xs text-slate-600">{c.criterionCode}</span>
                    </th>
                    <td className="py-2 pr-3 text-right">{c.actions}</td>
                    <td className="py-2 text-right">{c.employees}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <h3 className="font-semibold text-gov-text">Formy rozwoju</h3>
              <ul className="mt-1 space-y-0.5">
                {(Object.entries(s.byFormType) as [IprzFormType, number][]).map(([form, count]) => (
                  <li key={form} className="flex justify-between">
                    <span>{FORM_LABEL[form] ?? form}</span>
                    <strong>{count}</strong>
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <h3 className="font-semibold text-gov-text">Najczęstsze działania</h3>
              <ol className="mt-1 list-decimal space-y-0.5 pl-5">
                {s.topActions.map((a) => (
                  <li key={a.actionTitle}>
                    {a.actionTitle} <span className="text-slate-600">({a.count})</span>
                  </li>
                ))}
              </ol>
            </div>
          </div>
        </div>
      )}
    </Card>
  );
}
