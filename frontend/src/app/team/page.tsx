"use client";

import { useState } from "react";
import { RoleGate } from "@/components/RoleGate";
import { AiDisclaimer, Alert, Badge, Button, Card, MaskedPrompt, ModeBadge, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import type { HrPartnerResponse, TeamOverview } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

export default function TeamPage() {
  return (
    <RoleGate roles={["EVALUATOR", "DG"]}>
      <Team />
    </RoleGate>
  );
}

type Signal = TeamOverview["signals"][number];

/** Twarde fakty z bazy → workload_signals dla Wirtualnego HR Partnera (bez szacowania przez AI). */
function workloadSignals(s: Signal): Record<string, number> {
  const out: Record<string, number> = {
    absence_days: s.absenceDays6m,
    scope_changes: s.scopeChangesCount,
    overload_notes: s.overloadNotesCount,
  };
  if (s.daysSinceLastActivity !== null) out.days_since_last_note = s.daysSinceLastActivity;
  return out;
}

function scoreTone(score: number | undefined) {
  if (score === undefined) return "text-slate-400";
  if (score >= 7) return "bg-green-50 text-green-900";
  if (score <= 3) return "bg-red-50 text-red-900";
  return "text-gov-text";
}

function Team() {
  const { current } = useUser();
  const team = useAsync(() => api<TeamOverview>("/api/team/overview"), [current?.id]);
  const [advice, setAdvice] = useState<{ name: string; res: HrPartnerResponse } | null>(null);
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<unknown>(null);
  const t = team.data;
  const codes = t ? t.teamAverages.map((a) => a.code) : [];

  const analyze = async (s: Signal) => {
    setBusy(s.employeeId);
    setError(null);
    try {
      const path = s.activeCycleId ? `/api/core-ai/hr-partner?cycleId=${s.activeCycleId}` : "/api/core-ai/hr-partner";
      const notes = s.appreciationSnippet ? [s.appreciationSnippet] : [];
      setAdvice({ name: s.fullName, res: await api<HrPartnerResponse>(path, { body: { notes, workload_signals: workloadSignals(s) } }) });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gov-text">Mój zespół — Wirtualny HR Partner</h1>
      {team.loading && <Spinner />}
      <ProblemAlert error={team.error} />
      {t && t.competencies.length === 0 && <Alert tone="slate">Brak podwładnych ani zastępowanych cykli.</Alert>}
      {t && t.competencies.length > 0 && (
        <>
          <Card title="Mapa kompetencji (ostatnie oceny cząstkowe)">
            <div className="overflow-x-auto">
              <table className="w-full text-center text-sm">
                <caption className="sr-only">Pracownicy i kryteria</caption>
                <thead className="border-b border-slate-200 text-xs text-slate-600">
                  <tr>
                    <th className="py-2 pr-3 text-left">Pracownik</th>
                    {t.teamAverages.map((a) => (
                      <th key={a.code} className="px-1 py-2" title={a.name}>
                        {a.code}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {t.competencies.map((m) => (
                    <tr key={m.employeeId} className="border-b border-slate-100">
                      <td className="py-2 pr-3 text-left font-medium">
                        {m.fullName}
                        {m.positionTitle ? <span className="block text-xs text-slate-600">{m.positionTitle}</span> : null}
                      </td>
                      {codes.map((c) => (
                        <td key={c} className={`px-1 py-2 font-semibold ${scoreTone(m.scores[c])}`}>
                          {m.scores[c] ?? "·"}
                        </td>
                      ))}
                    </tr>
                  ))}
                  <tr className="bg-gov-section font-bold">
                    <td className="py-2 pr-3 text-left">Średnia zespołu</td>
                    {t.teamAverages.map((a) => (
                      <td key={a.code} className="px-1 py-2">
                        {String(a.average).replace(".", ",")}
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
            </div>
            <p className="mt-2 text-xs text-slate-600">Kody kryteriów — zał. nr 1 (najedź, aby zobaczyć nazwę). Dane wyłącznie z arkuszy ocen.</p>
          </Card>

          <div className="grid gap-6 lg:grid-cols-2">
            <Card title="Sugestie mentoringu wewnętrznego">
              {t.mentoring.length === 0 ? (
                <p className="text-sm text-slate-700">Brak par mentor (7–9 pkt) — mentee (≤ 5 pkt) w tym samym kryterium.</p>
              ) : (
                <ul className="space-y-2 text-sm">
                  {t.mentoring.map((p) => (
                    <li key={`${p.criterionCode}-${p.menteeId}`} className="rounded border border-slate-200 p-2">
                      <Badge tone="blue">{p.criterionName}</Badge> <strong>{p.mentorName}</strong> ({p.mentorScore} pkt) →{" "}
                      <strong>{p.menteeName}</strong> ({p.menteeScore} pkt)
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card title="Radar przeciążenia i momenty docenienia" actions={advice && <ModeBadge mode={advice.res.mode} />}>
              <ul className="space-y-3 text-sm">
                {t.signals.map((s) => {
                  const risk = s.overloadNotesCount > 0 || s.absenceDays6m > 20 || s.scopeChangesCount > 0;
                  return (
                    <li key={s.employeeId} className="rounded border border-slate-200 p-2">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <strong>{s.fullName}</strong>
                        <Button variant="ai" busy={busy === s.employeeId} onClick={() => analyze(s)}>
                          HR Partner
                        </Button>
                      </div>
                      <p className="mt-1 text-xs text-slate-700">
                        {risk && <Badge tone="amber">sygnał</Badge>} nieobecność (6 mies.): {s.absenceDays6m} dni · zmiany zakresu:{" "}
                        {s.scopeChangesCount} · notatki o przeciążeniu: {s.overloadNotesCount}
                        {s.daysSinceLastActivity !== null ? ` · ostatnia aktywność: ${s.daysSinceLastActivity} dni temu` : ""}
                      </p>
                      {s.appreciationSnippet && <p className="mt-1 text-xs text-green-900">Do docenienia: „{s.appreciationSnippet}”</p>}
                    </li>
                  );
                })}
              </ul>
              <div className="mt-3 space-y-3">
                <ProblemAlert error={error} />
                {advice && (
                  <>
                    <h3 className="text-sm font-bold">Rekomendacje dla: {advice.name}</h3>
                    <ul className="list-disc space-y-1 pl-5 text-sm">
                      {[...advice.res.conversation_starters, ...advice.res.organizational_suggestions].map((x) => (
                        <li key={x}>{x}</li>
                      ))}
                    </ul>
                    <MaskedPrompt prompt={advice.res.masked_prompt} />
                    <AiDisclaimer text={advice.res.ai_disclaimer} />
                  </>
                )}
              </div>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
