"use client";

import Link from "next/link";
import { Alert, Card, ProblemAlert, Spinner, StatusBadge } from "@/components/ui";
import { api } from "@/lib/api";
import { EMPLOYMENT_STATUS_LABEL, formatDate } from "@/lib/labels";
import type { HrAlert } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const TYPE_LABEL: Record<HrAlert["type"], string> = {
  TWO_NEGATIVE_TERMINATION: "Dwie kolejne oceny negatywne — rozwiązanie stosunku pracy (art. 71 ust. 1 pkt 1)",
  TWO_NEGATIVE_INFO: "Dwie kolejne oceny negatywne — informacja dla kadr",
  MANDATORY_GRADE: "Obligatoryjny kolejny stopień służbowy (art. 89 ust. 2)",
};

/** Alerty kadrowe z historii ocen (GET /api/hr/alerts) — backend wylicza skutki wg statusu zatrudnienia. */
export function HrAlertsCard({ reloadKey }: { reloadKey?: unknown }) {
  const alerts = useAsync(() => api<HrAlert[]>("/api/hr/alerts"), [reloadKey]);
  return (
    <Card title="Alerty kadrowe (skutki ocen okresowych)">
      {alerts.loading && <Spinner />}
      <ProblemAlert error={alerts.error} />
      {alerts.data && alerts.data.length === 0 && <Alert tone="green">Brak alertów kadrowych.</Alert>}
      {alerts.data && alerts.data.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {alerts.data.map((a) => (
            <li key={`${a.employeeId}-${a.type}`} className="py-3 text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge status={a.severity === "CRITICAL" ? "err" : "info"}>{a.severity === "CRITICAL" ? "krytyczny" : "informacyjny"}</StatusBadge>
                <span className="font-bold text-gov-text">{a.employeeName}</span>
                <span className="text-slate-600">{EMPLOYMENT_STATUS_LABEL[a.employmentStatus]}</span>
              </div>
              <p className="mt-1 font-medium">{TYPE_LABEL[a.type]}</p>
              <p className="text-slate-700">{a.message}</p>
              <p className="text-xs text-slate-600">
                Podstawa: {a.legalBasis} · ostatnia ocena {formatDate(a.since)}
                {a.cycleId ? (
                  <>
                    {" · "}
                    <Link className="text-gov-blue underline" href={`/cycles/${a.cycleId}`}>
                      cykl #{a.cycleId}
                    </Link>
                  </>
                ) : null}
              </p>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
