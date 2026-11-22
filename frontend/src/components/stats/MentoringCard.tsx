"use client";

import { useState } from "react";
import { AiComplianceBadge, Alert, Button, Card, ModeBadge, ProblemAlert } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api, coreAi } from "@/lib/api";
import type { TeamMentoringResponse, TeamOverview } from "@/lib/types";

/** Ocena ≥ 7 pkt = mocna strona (mentor), ≤ 5 pkt = obszar rozwojowy — jak w „Mój zespół”. */
const STRONG_MIN = 7;
const DEVELOPMENT_MAX = 5;
const MIN_MEMBERS = 2;
const MAX_ALIAS_LENGTH = 40;

/**
 * Parowanie mentoringowe (POST /api/core-ai/team-mentoring) z ostatnich ocen cząstkowych zespołu
 * (GET /api/team/overview — DG i przełożeni). Reguły deterministyczne, bez modelu AI.
 */
export function MentoringCard() {
  const { current } = useUser();
  const [result, setResult] = useState<TeamMentoringResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [tooSmall, setTooSmall] = useState(false);
  const roles = current ? [current.role, ...(current.secondaryRoles ?? [])] : [];
  const allowed = roles.includes("DG") || roles.includes("EVALUATOR");

  const pair = async () => {
    setBusy(true);
    setError(null);
    setTooSmall(false);
    try {
      const team = await api<TeamOverview>("/api/team/overview");
      const members = team.competencies
        .map((m) => ({
          alias: m.fullName.slice(0, MAX_ALIAS_LENGTH),
          strongCriteria: Object.entries(m.scores).filter(([, v]) => v >= STRONG_MIN).map(([code]) => code),
          developmentAreas: Object.entries(m.scores).filter(([, v]) => v <= DEVELOPMENT_MAX).map(([code]) => code),
        }))
        .filter((m) => m.strongCriteria.length || m.developmentAreas.length);
      if (members.length < MIN_MEMBERS) {
        setTooSmall(true);
        setResult(null);
        return;
      }
      setResult(await api<TeamMentoringResponse>(coreAi("team-mentoring"), { body: { members } }));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Parowanie mentoringowe" actions={result && <ModeBadge mode={result.mode} />}>
      <p className="text-sm text-slate-700">
        Łączy obszar rozwojowy jednej osoby (≤ {DEVELOPMENT_MAX} pkt) z mocną stroną innej (≥ {STRONG_MIN} pkt) w tym samym
        kryterium. Mentor wspiera najwyżej 2 osoby. Mentoring wewnętrzny to „20%” modelu 70-20-10 — koszt 0 zł.
      </p>
      <div className="mt-3">
        {allowed ? (
          <Button variant="ai" onClick={pair} busy={busy}>
            Zaproponuj pary mentoringowe
          </Button>
        ) : (
          <Alert tone="slate">Parowanie korzysta z macierzy kompetencji zespołu — dostępne dla dyrektora generalnego i przełożonych.</Alert>
        )}
      </div>
      <div className="mt-3 space-y-3">
        <ProblemAlert error={error} />
        {tooSmall && <Alert tone="amber">Za mało ocenionych osób w zespole, aby zaproponować pary (min. {MIN_MEMBERS}).</Alert>}
      </div>
      {result && (
        <div className="mt-4 space-y-4 text-sm">
          {result.pairs.length === 0 ? (
            <Alert tone="slate">Brak dopasowań mentor — osoba wspierana.</Alert>
          ) : (
            <ul className="space-y-2">
              {result.pairs.map((p) => (
                <li key={`${p.mentor}-${p.mentee}-${p.criterion}`} className="rounded border border-slate-200 p-2">
                  <p>
                    <strong>{p.mentor}</strong> (mentor) → <strong>{p.mentee}</strong> · {p.criterion}
                  </p>
                  <p className="text-xs text-slate-700">{p.rationale}</p>
                </li>
              ))}
            </ul>
          )}
          {result.unmatched.length > 0 && (
            <div>
              <h3 className="font-semibold text-gov-text">Bez dopasowania w zespole (szkolenie lub mentor spoza komórki)</h3>
              <ul className="mt-1 list-disc space-y-0.5 pl-5">
                {result.unmatched.map((u) => (
                  <li key={`${u.mentee}-${u.criterion}`}>
                    {u.mentee} — {u.criterion}
                  </li>
                ))}
              </ul>
            </div>
          )}
          <AiComplianceBadge meta={result.complianceMeta} />
        </div>
      )}
    </Card>
  );
}
