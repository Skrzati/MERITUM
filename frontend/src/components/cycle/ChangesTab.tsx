"use client";

import { useState } from "react";
import { Alert, Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import { formatDate, today, type Tone } from "@/lib/labels";
import { useAsync } from "@/lib/useAsync";
import type { RescheduleMode, RescheduleRequest, RescheduleStatus, ScopeChange, ScopeChangeOutcome } from "./contracts";
import type { CycleView } from "./context";

const MODES: { mode: RescheduleMode; label: string; byEmployee: boolean }[] = [
  { mode: "EVALUATOR_CHANGE", label: "pkt 1 — zmiana na stanowisku oceniającego", byEmployee: false },
  { mode: "EMPLOYEE_ABSENCE", label: "pkt 2 — dłuższa usprawiedliwiona nieobecność ocenianego", byEmployee: false },
  { mode: "EVALUATOR_ABSENCE", label: "pkt 3 — dłuższa usprawiedliwiona nieobecność oceniającego", byEmployee: false },
  { mode: "EMPLOYEE_REQUEST", label: "pkt 4 — wniosek ocenianego", byEmployee: true },
];
const RESCHEDULE_STATUS: Record<RescheduleStatus, { label: string; tone: Tone }> = {
  PENDING: { label: "oczekuje na decyzję", tone: "amber" },
  APPLIED: { label: "zastosowano", tone: "green" },
  REJECTED: { label: "odrzucono", tone: "red" },
};
const SCOPE_OUTCOME: Record<ScopeChangeOutcome, { label: string; tone: Tone }> = {
  NONE: { label: "bez skutków dla oceny", tone: "slate" },
  CRITERIA_RESET: { label: "ponowne ustalenie kryteriów (§ 9 ust. 1)", tone: "amber" },
  EVALUATION_REQUIRED: { label: "wymagana ocena (art. 81 ust. 6)", tone: "red" },
};

export function ChangesTab({ view }: { view: CycleView }) {
  const { cycle, isEvaluator, isEmployee, isOfficeHr, refresh } = view;
  const { userName } = useUser();
  const active = cycle.status !== "COMPLETED" && cycle.status !== "APPEALED";
  const reschedules = useAsync(() => api<RescheduleRequest[]>(`/api/cycles/${cycle.id}/reschedule`), [cycle.id, cycle.dueDate]);
  const scopes = useAsync(() => api<ScopeChange[]>(`/api/cycles/${cycle.id}/scope-changes`), [cycle.id, cycle.status]);

  const [mode, setMode] = useState<RescheduleMode>(isEmployee ? "EMPLOYEE_REQUEST" : "EVALUATOR_CHANGE");
  const [newDueDate, setNewDueDate] = useState("");
  const [reason, setReason] = useState("");
  const [comments, setComments] = useState<Record<number, string>>({});
  const [scopeDate, setScopeDate] = useState(today());
  const [scopeTitle, setScopeTitle] = useState("");
  const [scopeDescription, setScopeDescription] = useState("");
  const [significant, setSignificant] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  const allowedModes = MODES.filter((m) => (isEmployee ? m.byEmployee : isEvaluator && !m.byEmployee));
  const latestNotice = reschedules.data?.filter((r) => r.noticeText).slice(-1)[0];

  const run = async (key: string, fn: () => Promise<unknown>, after: () => void) => {
    setBusy(key);
    setError(null);
    try {
      await fn();
      after();
      await refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const submitReschedule = () =>
    run(
      "reschedule",
      () => api<RescheduleRequest>(`/api/cycles/${cycle.id}/reschedule`, { body: { mode, newDueDate, reason } }),
      () => {
        setReason("");
        setNewDueDate("");
        void reschedules.reload();
      },
    );
  const decide = (requestId: number, accept: boolean) =>
    run(
      `decide-${requestId}`,
      () =>
        api<RescheduleRequest>(`/api/cycles/${cycle.id}/reschedule/${requestId}/decision`, {
          body: { accept, comment: comments[requestId] ?? "" },
        }),
      () => void reschedules.reload(),
    );
  const submitScope = () =>
    run(
      "scope",
      () =>
        api<ScopeChange>(`/api/cycles/${cycle.id}/scope-changes`, {
          body: { changeDate: scopeDate, newPositionTitle: scopeTitle || null, description: scopeDescription, significant },
        }),
      () => {
        setScopeDescription("");
        setScopeTitle("");
        void scopes.reload();
      },
    );

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <div className="space-y-6">
        <Card title="Zmiana terminu oceny (art. 81 ust. 8, § 8)">
          <p className="mb-3 text-sm text-slate-700">
            Obecny termin oceny: <strong>{formatDate(cycle.dueDate)}</strong>. Zmiana jest dopuszczalna, gdy od ostatniej
            oceny minęło ponad 6 miesięcy.
          </p>
          {reschedules.loading && !reschedules.data && <Spinner />}
          <ProblemAlert error={reschedules.error} />
          {reschedules.data?.length ? (
            <ul className="space-y-3">
              {reschedules.data.map((r) => (
                <li key={r.id} className="rounded-md border border-slate-200 p-3 text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium">
                      pkt {r.statutePoint} · {formatDate(r.previousDueDate)} → {formatDate(r.newDueDate ?? r.proposedDueDate)}
                    </span>
                    <Badge tone={RESCHEDULE_STATUS[r.status].tone}>{RESCHEDULE_STATUS[r.status].label}</Badge>
                  </div>
                  <p className="mt-1 text-slate-800">{r.reason}</p>
                  <p className="mt-1 text-xs text-slate-600">
                    {userName(r.requestedBy)} · {formatDate(r.createdAt)}
                    {r.decisionComment ? ` · decyzja: ${r.decisionComment}` : ""}
                  </p>
                  {r.status === "PENDING" && isEvaluator && (
                    <div className="mt-3 space-y-2 border-t border-slate-200 pt-3">
                      <Field label="Komentarz do decyzji">
                        <input
                          className={inputClass}
                          value={comments[r.id] ?? ""}
                          onChange={(e) => setComments((c) => ({ ...c, [r.id]: e.target.value }))}
                        />
                      </Field>
                      <div className="flex gap-2">
                        <Button onClick={() => decide(r.id, true)} busy={busy === `decide-${r.id}`}>
                          Akceptuj wniosek
                        </Button>
                        <Button variant="danger" onClick={() => decide(r.id, false)} busy={busy === `decide-${r.id}`}>
                          Odrzuć
                        </Button>
                      </div>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            reschedules.data && <p className="text-sm text-slate-700">Brak zmian terminu.</p>
          )}

          {active && allowedModes.length > 0 && (
            <div className="mt-5 space-y-3 border-t border-slate-200 pt-4">
              <h3 className="text-sm font-semibold text-navy-900">{isEmployee ? "Wniosek o zmianę terminu" : "Zmień termin oceny"}</h3>
              <Field label="Przesłanka (art. 81 ust. 8)">
                <select className={inputClass} value={mode} onChange={(e) => setMode(e.target.value as RescheduleMode)}>
                  {allowedModes.map((m) => (
                    <option key={m.mode} value={m.mode}>
                      {m.label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Nowy termin oceny">
                <input type="date" className={inputClass} value={newDueDate} onChange={(e) => setNewDueDate(e.target.value)} />
              </Field>
              <Field label="Uzasadnienie">
                <textarea className={inputClass} rows={2} value={reason} onChange={(e) => setReason(e.target.value)} />
              </Field>
              <Button onClick={submitReschedule} busy={busy === "reschedule"} disabled={!newDueDate || !reason.trim()}>
                {isEmployee ? "Złóż wniosek" : "Zmień termin i wygeneruj zawiadomienie"}
              </Button>
            </div>
          )}
        </Card>

        {latestNotice?.noticeText && (
          <Card title="Pisemne zawiadomienie o zmianie terminu (§ 8)">
            <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded bg-slate-50 p-3 font-serif text-sm text-slate-900 ring-1 ring-inset ring-slate-200">
              {latestNotice.noticeText}
            </pre>
          </Card>
        )}
      </div>

      <Card title="Zmiany zakresu obowiązków (§ 9, art. 81 ust. 6)">
        {scopes.loading && !scopes.data && <Spinner />}
        <ProblemAlert error={scopes.error} />
        {scopes.data?.length ? (
          <ul className="space-y-3">
            {scopes.data.map((s) => (
              <li key={s.id} className="rounded-md border border-slate-200 p-3 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-medium">
                    {formatDate(s.changeDate)}
                    {s.newPositionTitle ? ` · ${s.newPositionTitle}` : ""}
                  </span>
                  <Badge tone={SCOPE_OUTCOME[s.outcome].tone}>{SCOPE_OUTCOME[s.outcome].label}</Badge>
                </div>
                <p className="mt-1 text-slate-800">{s.description}</p>
                <p className="mt-1 text-xs text-slate-600">
                  {s.significant ? "istotna zmiana" : "zmiana nieistotna"} · zarejestrował(a): {userName(s.recordedBy)}
                  {s.actionDeadline ? ` · termin działania: ${formatDate(s.actionDeadline)}` : ""}
                </p>
              </li>
            ))}
          </ul>
        ) : (
          scopes.data && <p className="text-sm text-slate-700">Brak zarejestrowanych zmian zakresu obowiązków.</p>
        )}

        {active && (isEvaluator || isOfficeHr) && (
          <div className="mt-5 space-y-3 border-t border-slate-200 pt-4">
            <h3 className="text-sm font-semibold text-navy-900">Zarejestruj zmianę zakresu obowiązków</h3>
            <Field label="Data zmiany">
              <input type="date" className={inputClass} value={scopeDate} onChange={(e) => setScopeDate(e.target.value)} />
            </Field>
            <Field label="Nowe stanowisko (opcjonalnie)">
              <input className={inputClass} value={scopeTitle} onChange={(e) => setScopeTitle(e.target.value)} />
            </Field>
            <Field label="Opis zmiany">
              <textarea className={inputClass} rows={2} value={scopeDescription} onChange={(e) => setScopeDescription(e.target.value)} />
            </Field>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" className="h-4 w-4 accent-navy-700" checked={significant} onChange={(e) => setSignificant(e.target.checked)} />
              Istotna zmiana zakresu obowiązków
            </label>
            <Alert tone="blue">
              Skutek wylicza silnik reguł: istotna zmiana w ciągu pierwszych 6 miesięcy → ponowne ustalenie kryteriów
              (§ 9 ust. 1); później → sporządzenie oceny (art. 81 ust. 6).
            </Alert>
            <Button onClick={submitScope} busy={busy === "scope"} disabled={!scopeDescription.trim()}>
              Zarejestruj zmianę
            </Button>
          </div>
        )}
      </Card>

      <div className="lg:col-span-2">
        <ProblemAlert error={error} />
      </div>
    </div>
  );
}
