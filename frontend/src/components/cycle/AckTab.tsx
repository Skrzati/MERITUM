"use client";

import { useState } from "react";
import { Alert, Button, Card, Field, inputClass, ProblemAlert } from "@/components/ui";
import { download, api } from "@/lib/api";
import { formatDate, today } from "@/lib/labels";
import type { Cycle, DgDecision } from "@/lib/types";
import type { CycleView } from "./context";
import { ScorePicker } from "./ScorePicker";

export function AckTab({ view }: { view: CycleView }) {
  const { cycle, isEmployee, isEvaluator, isOfficeHr, isOfficeDg, refresh } = view;
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [refusalReason, setRefusalReason] = useState("");
  const [refusalDate, setRefusalDate] = useState(today());
  const [appealText, setAppealText] = useState("");
  const [decision, setDecision] = useState<DgDecision>("REDO");
  const [dgComment, setDgComment] = useState("");
  const [dgScores, setDgScores] = useState<Record<string, number>>(cycle.scores ?? {});
  const [dgJustification, setDgJustification] = useState(cycle.justification ?? "");

  const appealOpen = cycle.status === "COMPLETED" && (!cycle.appealDeadline || cycle.appealDeadline >= today());

  const run = async (key: string, fn: () => Promise<unknown>) => {
    setBusy(key);
    setError(null);
    try {
      await fn();
      await refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const pdf = async () => {
    setBusy("pdf");
    setError(null);
    try {
      await download(`/api/cycles/${cycle.id}/pdf`, `arkusz-oceny-${cycle.id}.pdf`);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card title="Stan zapoznania i sprzeciwu">
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <dt className="text-slate-600">Data zapoznania</dt>
          <dd>{formatDate(cycle.acknowledgedAt)}</dd>
          <dt className="text-slate-600">Termin na sprzeciw (7 dni)</dt>
          <dd>{formatDate(cycle.appealDeadline)}</dd>
          {cycle.refusalDate && (
            <>
              <dt className="text-slate-600">Odmowa zapoznania</dt>
              <dd>
                {formatDate(cycle.refusalDate)} — {cycle.refusalReason}
              </dd>
            </>
          )}
          {cycle.appealedAt && (
            <>
              <dt className="text-slate-600">Sprzeciw złożony</dt>
              <dd>{formatDate(cycle.appealedAt)}</dd>
            </>
          )}
          {cycle.dgDecision && (
            <>
              <dt className="text-slate-600">Decyzja DG</dt>
              <dd>
                {cycle.dgDecision === "CHANGE" ? "zmiana oceny" : "ponowne sporządzenie"} ({formatDate(cycle.dgDecidedAt)})
                {cycle.dgComment ? ` — ${cycle.dgComment}` : ""}
              </dd>
            </>
          )}
        </dl>
        {cycle.appealText && <p className="mt-3 rounded bg-slate-50 p-2 text-sm ring-1 ring-inset ring-slate-200">„{cycle.appealText}”</p>}

        <div className="mt-5 border-t border-slate-200 pt-4">
          <Button onClick={pdf} busy={busy === "pdf"}>
            ⬇ Pobierz PDF/A
          </Button>
          <p className="mt-1 text-xs text-slate-600">Arkusz wg wzoru z rozporządzenia, generowany przez Gotenberg (PDF/A-2b).</p>
        </div>
        <div className="mt-4">
          <ProblemAlert error={error} />
        </div>
      </Card>

      <div className="space-y-6">
        {cycle.status === "AWAITING_ACK" && isEmployee && (
          <Card title="Zapoznanie z oceną">
            <p className="mb-3 text-sm text-slate-700">Po potwierdzeniu biegnie 7-dniowy termin na wniesienie sprzeciwu do DG.</p>
            <Button onClick={() => run("ack", () => api<Cycle>(`/api/cycles/${cycle.id}/acknowledge`, { method: "POST" }))} busy={busy === "ack"}>
              Potwierdź zapoznanie
            </Button>
          </Card>
        )}

        {cycle.status === "AWAITING_ACK" && (isEvaluator || isOfficeHr) && (
          <Card title="Odmowa zapoznania (fikcja zapoznania)">
            <div className="space-y-3">
              <Field label="Powód / okoliczności">
                <textarea className={inputClass} rows={2} value={refusalReason} onChange={(e) => setRefusalReason(e.target.value)} />
              </Field>
              <Field label="Data odmowy">
                <input type="date" max={today()} className={inputClass} value={refusalDate} onChange={(e) => setRefusalDate(e.target.value)} />
              </Field>
              <Button
                variant="secondary"
                disabled={!refusalReason.trim()}
                busy={busy === "refusal"}
                onClick={() =>
                  run("refusal", () => api<Cycle>(`/api/cycles/${cycle.id}/refusal`, { body: { reason: refusalReason, date: refusalDate } }))
                }
              >
                Stwierdź odmowę / fikcja zapoznania
              </Button>
            </div>
          </Card>
        )}

        {isEmployee && appealOpen && (
          <Card title="Sprzeciw od oceny">
            <div className="space-y-3">
              <Field label="Treść sprzeciwu" hint={`Termin: ${formatDate(cycle.appealDeadline)}`}>
                <textarea className={inputClass} rows={4} value={appealText} onChange={(e) => setAppealText(e.target.value)} />
              </Field>
              <Button
                variant="danger"
                disabled={!appealText.trim()}
                busy={busy === "appeal"}
                onClick={() => run("appeal", () => api<Cycle>(`/api/cycles/${cycle.id}/appeal`, { body: { content: appealText } }))}
              >
                Złóż sprzeciw
              </Button>
            </div>
          </Card>
        )}

        {cycle.status === "APPEALED" && isOfficeDg && (
          <Card title="Decyzja Dyrektora Generalnego">
            <div className="space-y-3">
              <fieldset className="flex gap-4 text-sm">
                <legend className="sr-only">Rodzaj decyzji</legend>
                {(["REDO", "CHANGE"] as DgDecision[]).map((d) => (
                  <label key={d} className="flex items-center gap-2">
                    <input type="radio" name="dg-decision" className="h-4 w-4 accent-navy-700" checked={decision === d} onChange={() => setDecision(d)} />
                    {d === "REDO" ? "Ponowne sporządzenie oceny" : "Zmiana oceny"}
                  </label>
                ))}
              </fieldset>
              {decision === "CHANGE" && cycle.criteria && (
                <>
                  <ScorePicker criteria={cycle.criteria} scores={dgScores} onChange={(code, v) => setDgScores((s) => ({ ...s, [code]: v }))} />
                  <Field label="Uzasadnienie zmienionej oceny">
                    <textarea className={inputClass} rows={3} value={dgJustification} onChange={(e) => setDgJustification(e.target.value)} />
                  </Field>
                </>
              )}
              <Field label="Komentarz DG">
                <textarea className={inputClass} rows={2} value={dgComment} onChange={(e) => setDgComment(e.target.value)} />
              </Field>
              <Button
                busy={busy === "dg"}
                onClick={() =>
                  run("dg", () =>
                    api<Cycle>(`/api/cycles/${cycle.id}/appeal/decision`, {
                      body: {
                        decision,
                        comment: dgComment,
                        scores: decision === "CHANGE" ? dgScores : null,
                        justification: decision === "CHANGE" ? dgJustification : null,
                      },
                    }),
                  )
                }
              >
                Zatwierdź decyzję DG
              </Button>
            </div>
          </Card>
        )}

        {cycle.status === "COMPLETED" && !appealOpen && <Alert tone="slate">Termin na sprzeciw upłynął — ocena jest ostateczna.</Alert>}
      </div>
    </div>
  );
}
