"use client";

import { useState } from "react";
import {
  AiComplianceBadge,
  AiDisclaimer,
  Alert,
  Badge,
  Button,
  Card,
  Field,
  inputClass,
  MaskedPrompt,
  ModeBadge,
  ProblemAlert,
  VisibilityBanner,
} from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api, coreAi } from "@/lib/api";
import { formatDate, INTERVIEW_LEGAL_STATUS, LEVEL_LABEL, MAX_CIVIL_SERVICE_GRADE, OUTCOME_LABEL, previewAverage } from "@/lib/labels";
import type {
  ConsistencyResponse,
  EvaluationResponse,
  EvaluationResult,
  InterviewPrepResponse,
  Outcome,
  PerformanceLevel,
  ScopeChange,
  SocraticResponse,
} from "@/lib/types";
import type { CycleView } from "./context";
import { DevelopmentCatalog, fetchInterviewPrep } from "./InterviewTab";
import { ScorePicker } from "./ScorePicker";

const EDITABLE = ["OBSERVATION", "EVALUATION_DRAFT"];
const LOWEST_SCORE = 1;
/** Zmiana zakresu obowiązków w tylu ostatnich dniach okresu — przypomnienie o okresie wdrożenia (RECENT_SCOPE_CHANGE). */
const RECENT_SCOPE_CHANGE_DAYS = 90;
const DAY_MS = 86_400_000;

const WARNING_LABEL: Record<string, string> = {
  CRITERIA_MISMATCH: "Niezgodność kryteriów",
  FOREIGN_NAME: "Obce imię lub nazwisko",
  LEVEL_MISMATCH: "Niezgodny poziom",
  AVERAGE_MISMATCH: "Niezgodna średnia",
  DUPLICATE_TEXT: "Powtórzony tekst",
  GENDER_MISMATCH: "Niezgodna forma gramatyczna",
  RECENCY_BIAS: "Efekt świeżości (notatki głównie z końca okresu)",
  RECENT_SCOPE_CHANGE: "Niedawna zmiana zakresu obowiązków",
};

async function recentScopeChange(cycleId: number, periodEnd: string): Promise<boolean> {
  try {
    const changes = await api<ScopeChange[]>(`/api/cycles/${cycleId}/scope-changes`);
    const from = Date.parse(periodEnd) - RECENT_SCOPE_CHANGE_DAYS * DAY_MS;
    return changes.some((c) => Date.parse(c.changeDate) >= from);
  } catch {
    return false;
  }
}

export function EvaluationTab({ view, onOpenInterview }: { view: CycleView; onOpenInterview: () => void }) {
  const { cycle, notes, employee, isEvaluator, refresh } = view;
  const { userName } = useUser();
  const criteria = cycle.criteria ?? [];
  const editable = isEvaluator && EDITABLE.includes(cycle.status);
  const civilServant = employee?.civilServant ?? false;
  const highestGrade = (employee?.civilServiceGrade ?? 0) >= MAX_CIVIL_SERVICE_GRADE;
  const overdue = cycle.overdueDays > 0;

  const [scores, setScores] = useState<Record<string, number>>(cycle.scores ?? {});
  const [justification, setJustification] = useState(cycle.justification ?? "");
  const [iprz, setIprz] = useState(cycle.iprzNotes ?? "");
  const [gradeRequested, setGradeRequested] = useState(cycle.gradeRequest?.requested ?? false);
  const [motivation, setMotivation] = useState(cycle.gradeRequest?.motivation ?? "");
  const [gender, setGender] = useState("");
  const [interviewDate, setInterviewDate] = useState(cycle.evaluationInterviewDate ?? "");
  const [delayReason, setDelayReason] = useState(cycle.delayReason ?? "");
  const fromHistory = cycle.previousEvaluation && cycle.previousEvaluation.source !== "MANUAL" ? cycle.previousEvaluation : null;
  const manualPrev = cycle.previousEvaluation?.source === "MANUAL" ? cycle.previousEvaluation : null;
  const [prevOutcome, setPrevOutcome] = useState<Outcome | "">(manualPrev?.outcome ?? "");
  const [prevLevel, setPrevLevel] = useState<PerformanceLevel | "">(manualPrev?.level ?? "");
  const [prevDate, setPrevDate] = useState(manualPrev?.evaluatedOn ?? "");
  const [iprzPrep, setIprzPrep] = useState<InterviewPrepResponse | null>(null);

  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [result, setResult] = useState<EvaluationResult | null>(null);
  const [draftSaved, setDraftSaved] = useState(false);
  const [socratic, setSocratic] = useState<SocraticResponse | null>(null);
  const [consistency, setConsistency] = useState<ConsistencyResponse | null>(null);

  const average = previewAverage(criteria.map((c) => scores[c.code]));
  const lowestScored = criteria.filter((c) => scores[c.code] === LOWEST_SCORE);

  const run = async (key: string, fn: () => Promise<void>) => {
    setBusy(key);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  /** Ręczne dane poprzedniej oceny tylko dla urzędnika SC i tylko gdy brak ich w historii (schemat pól, cz. II). */
  const previousEvaluationBody = () => {
    if (!civilServant || fromHistory) return null;
    if (prevOutcome) return { outcome: prevOutcome, level: prevLevel || null, evaluatedOn: prevDate || null };
    return manualPrev ? { outcome: null } : null;
  };

  const evaluate = (submit: boolean) =>
    run(submit ? "submit" : "draft", async () => {
      setDraftSaved(false);
      const res = await api<EvaluationResponse>(`/api/cycles/${cycle.id}/evaluate`, {
        method: "PUT",
        body: {
          scores,
          justification,
          iprzNotes: iprz,
          gradeRequest: civilServant && !highestGrade && gradeRequested ? { requested: true, motivation } : null,
          evaluationInterviewDate: interviewDate || null,
          delayReason: overdue && delayReason.trim() ? delayReason : null,
          previousEvaluation: previousEvaluationBody(),
          submit,
        },
      });
      setResult(res.result);
      setDraftSaved(!submit);
      await refresh();
    });

  const askSocratic = () =>
    run("socratic", async () => {
      setSocratic(
        await api<SocraticResponse>(coreAi("socratic-draft", cycle.id), {
          body: {
            employee_name: userName(cycle.employeeId),
            sheet_type: cycle.sheetType,
            criteria_scores: criteria.map((c) => ({ code: c.code, name: c.name, score: scores[c.code] ?? null })),
            notes: (notes ?? []).map((n) => n.content),
          },
        }),
      );
    });

  const checkConsistency = () =>
    run("consistency", async () => {
      setConsistency(
        await api<ConsistencyResponse>(coreAi("consistency-check", cycle.id), {
          body: {
            employee_name: userName(cycle.employeeId),
            employee_gender: gender || null,
            criteria_part1: criteria.map((c) => c.name),
            criteria_part2: criteria.filter((c) => scores[c.code] !== undefined).map((c) => c.name),
            average_score: Number(average ?? cycle.averageScore ?? 5),
            level: null,
            justification,
            grade_request_text: civilServant && !highestGrade && gradeRequested ? motivation : null,
            notes: (notes ?? []).map((n) => ({ date: n.createdAt.slice(0, 10), text: n.content })),
            periodEnd: cycle.dueDate,
            recentScopeChange: await recentScopeChange(cycle.id, cycle.dueDate),
          },
        }),
      );
    });

  const suggestIprz = () =>
    run("iprz", async () => setIprzPrep(await fetchInterviewPrep(view, userName(cycle.employeeId), "EVALUATION")));
  const appendIprz = (criterion: string, actions: string[]) =>
    setIprz((text) => `${text.trim() ? `${text.trim()}\n` : ""}${criterion}: ${actions.join("; ")}`);

  if (!criteria.length) return <Alert tone="amber">Najpierw należy ustalić i potwierdzić kryteria oceny.</Alert>;

  return (
    <div className="grid gap-6 xl:grid-cols-5">
      <div className="space-y-6 xl:col-span-3">
        {overdue && cycle.status !== "AWAITING_ACK" && (
          <Alert tone="amber" title={`Termin oceny minął ${cycle.overdueDays} dni temu`}>
            Ocenę można sporządzić po terminie — nie jest to blokowane. Wskaż przyczynę opóźnienia; zostanie ona
            zapisana w audycie i na wydruku arkusza.
          </Alert>
        )}
        <Card
          title="Część II — oceny cząstkowe"
          actions={
            <div className="text-right text-sm">
              <span className="text-slate-700">Średnia (podgląd): </span>
              <strong className="text-lg text-navy-900">{average ?? "—"}</strong>
            </div>
          }
        >
          {!editable && (
            <p className="mb-3 text-sm text-slate-700">
              {isEvaluator ? "Ocena jest zablokowana na tym etapie procesu." : "Podgląd — ocenę wypełnia oceniający."}
            </p>
          )}
          <ScorePicker
            criteria={criteria}
            scores={scores}
            expectations={cycle.criteriaExpectations}
            disabled={!editable}
            onChange={(code, v) => setScores((s) => ({ ...s, [code]: v }))}
          />
          {lowestScored.length > 0 && (
            <div className="mt-3">
              <Alert tone="red" title={`1 pkt (znacznie poniżej oczekiwań): ${lowestScored.map((c) => c.name).join(", ")}`}>
                <ul className="list-disc space-y-1 pl-5">
                  <li>Ocena 1 pkt w dowolnym kryterium oznacza ocenę negatywną — uzasadnienie jest obowiązkowe.</li>
                  <li>Po ocenie negatywnej przeprowadza się ponowną ocenę po 6 miesiącach (art. 81 ust. 9).</li>
                  {employee?.employmentStatus === "URZEDNIK_SC" && (
                    <li>
                      <strong>Urzędnik SC:</strong> dwie kolejne oceny negatywne oznaczają rozwiązanie stosunku pracy (art. 71 ust. 1 pkt 1).
                    </li>
                  )}
                </ul>
              </Alert>
            </div>
          )}
          <p className="mt-2 text-xs text-slate-600">
            Wynik wiążący (poziom, ocena pozytywna/negatywna) wylicza silnik reguł § 11 po zatwierdzeniu. Ocena 1 pkt w dowolnym kryterium oznacza ocenę negatywną.
          </p>
        </Card>

        <Card title="Uzasadnienie i IPRZ">
          <div className="space-y-4">
            <VisibilityBanner kind="official" />
            <Field label="Uzasadnienie oceny" hint="Obowiązkowe przy ocenie negatywnej.">
              <textarea className={inputClass} rows={6} value={justification} disabled={!editable} onChange={(e) => setJustification(e.target.value)} />
            </Field>
            <Field label="Wnioski dotyczące IPRZ (obowiązkowe)">
              <textarea className={inputClass} rows={3} value={iprz} disabled={!editable} onChange={(e) => setIprz(e.target.value)} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Data rozmowy oceniającej (§ 10)" hint={INTERVIEW_LEGAL_STATUS.mandatory}>
                <input type="date" className={inputClass} value={interviewDate} disabled={!editable} onChange={(e) => setInterviewDate(e.target.value)} />
              </Field>
              {overdue && (
                <Field label="Powód opóźnienia" hint="Nie blokuje zatwierdzenia oceny.">
                  <input className={inputClass} value={delayReason} disabled={!editable} onChange={(e) => setDelayReason(e.target.value)} />
                </Field>
              )}
            </div>
            {civilServant && (
              <fieldset className="rounded-md border border-slate-200 p-3">
                <legend className="px-1 text-sm font-medium">Dane dotyczące poprzedniej oceny okresowej</legend>
                {fromHistory ? (
                  <p className="text-sm text-slate-800">
                    {OUTCOME_LABEL[fromHistory.outcome]} · {LEVEL_LABEL[fromHistory.level]} · {formatDate(fromHistory.evaluatedOn)}{" "}
                    <span className="text-xs text-slate-600">(uzupełnione automatycznie z historii ocen)</span>
                  </p>
                ) : (
                  <div className="grid gap-3 sm:grid-cols-3">
                    <Field label="Ocena">
                      <select className={inputClass} value={prevOutcome} disabled={!editable} onChange={(e) => setPrevOutcome(e.target.value as Outcome | "")}>
                        <option value="">brak</option>
                        <option value="POSITIVE">pozytywna</option>
                        <option value="NEGATIVE">negatywna</option>
                      </select>
                    </Field>
                    <Field label="Poziom">
                      <select className={inputClass} value={prevLevel} disabled={!editable || !prevOutcome} onChange={(e) => setPrevLevel(e.target.value as PerformanceLevel | "")}>
                        <option value="">—</option>
                        {(Object.keys(LEVEL_LABEL) as PerformanceLevel[]).map((l) => (
                          <option key={l} value={l}>
                            {LEVEL_LABEL[l]}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Data sporządzenia">
                      <input type="date" className={inputClass} value={prevDate} disabled={!editable || !prevOutcome} onChange={(e) => setPrevDate(e.target.value)} />
                    </Field>
                  </div>
                )}
              </fieldset>
            )}
            {civilServant && highestGrade ? (
              <Alert tone="slate" title="Część III — oceniany ma najwyższy (9.) stopień służbowy">
                Wniosek o przyznanie kolejnego stopnia służbowego nie może zostać złożony.
              </Alert>
            ) : civilServant ? (
              <fieldset className="rounded-md border border-slate-200 p-3">
                <legend className="px-1 text-sm font-medium">Część III — wniosek o kolejny stopień służbowy</legend>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    className="h-4 w-4 accent-navy-700"
                    checked={gradeRequested}
                    disabled={!editable}
                    onChange={(e) => setGradeRequested(e.target.checked)}
                  />
                  Składam wniosek o przyznanie kolejnego stopnia służbowego
                </label>
                {gradeRequested && (
                  <div className="mt-3">
                    <Field label="Umotywowanie wniosku (obowiązkowe)">
                      <textarea className={inputClass} rows={3} value={motivation} disabled={!editable} onChange={(e) => setMotivation(e.target.value)} />
                    </Field>
                  </div>
                )}
              </fieldset>
            ) : (
              <p className="text-xs text-slate-600">Część III niedostępna — oceniany nie jest urzędnikiem służby cywilnej.</p>
            )}
          </div>
        </Card>

        {editable && (
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => evaluate(false)} busy={busy === "draft"}>
              Zapisz wersję roboczą
            </Button>
            <Button onClick={() => evaluate(true)} busy={busy === "submit"}>
              Zatwierdź ocenę
            </Button>
          </div>
        )}
        <ProblemAlert error={error} />
        {draftSaved && !error && <Alert tone="green">Wersja robocza zapisana.</Alert>}
        {(result || cycle.resultExplanation) && !error && <ResultBox result={result} fallback={cycle} />}
      </div>

      <div className="space-y-6 xl:col-span-2">
        <Card title="Sokratejskie AI" actions={socratic && <ModeBadge mode={socratic.mode} />}>
          <p className="text-sm text-slate-700">
            Asystent zadaje pytania „dlaczego?” i przygotowuje szkic uzasadnienia. <strong>Nigdy nie proponuje punktów.</strong>
          </p>
          <div className="mt-3">
            <Button variant="ai" onClick={askSocratic} busy={busy === "socratic"} disabled={!isEvaluator}>
              Sokratejskie AI
            </Button>
          </div>
          {socratic && (
            <div className="mt-4 space-y-4">
              <div>
                <h3 className="text-sm font-semibold text-navy-900">Pytania pomocnicze</h3>
                <ol className="mt-1 list-decimal space-y-1 pl-5 text-sm text-slate-800">
                  {socratic.socratic_questions.map((q) => (
                    <li key={q}>{q}</li>
                  ))}
                </ol>
              </div>
              <div>
                <h3 className="flex items-center gap-2 text-sm font-semibold text-navy-900">
                  Szkic uzasadnienia <Badge tone="blue">AI</Badge>
                </h3>
                <p className="mt-1 whitespace-pre-wrap rounded bg-slate-50 p-2 text-sm text-slate-800 ring-1 ring-inset ring-slate-200">
                  {socratic.draft_justification}
                </p>
                {editable && (
                  <Button variant="secondary" className="mt-2" onClick={() => setJustification(socratic.draft_justification)}>
                    Wstaw szkic do uzasadnienia (do edycji)
                  </Button>
                )}
              </div>
              <AiComplianceBadge meta={socratic.complianceMeta} />
              <AiDisclaimer text={socratic.ai_disclaimer} />
              <MaskedPrompt prompt={socratic.masked_prompt} />
            </div>
          )}
        </Card>

        {editable && (
          <Card title="Podpowiedzi do IPRZ" actions={iprzPrep && <ModeBadge mode={iprzPrep.mode} />}>
            <p className="text-sm text-slate-700">
              Działania rozwojowe dla kryteriów z najniższymi ocenami — do wyboru i edycji.{" "}
              <button type="button" onClick={onOpenInterview} className="font-medium text-navy-700 underline">
                Pełne przygotowanie rozmowy oceniającej →
              </button>
            </p>
            <div className="mt-3">
              <Button variant="ai" onClick={suggestIprz} busy={busy === "iprz"}>
                AI: Podpowiedz działania rozwojowe
              </Button>
            </div>
            {iprzPrep && (
              <div className="mt-4 space-y-3">
                <DevelopmentCatalog entries={iprzPrep.development_catalog} actionLabel={() => "Dopisz do IPRZ"} onPick={appendIprz} />
                <AiComplianceBadge meta={iprzPrep.complianceMeta} />
                <AiDisclaimer text={iprzPrep.ai_disclaimer} />
              </div>
            )}
          </Card>
        )}

        <Card title="Kontrola spójności">
          <Field label="Płeć ocenianego (do kontroli form gramatycznych)" hint="Nie jest zgadywana na podstawie imienia.">
            <select className={inputClass} value={gender} onChange={(e) => setGender(e.target.value)}>
              <option value="">nie podano</option>
              <option value="K">kobieta</option>
              <option value="M">mężczyzna</option>
            </select>
          </Field>
          <div className="mt-3">
            <Button variant="ai" onClick={checkConsistency} busy={busy === "consistency"}>
              Kontrola spójności
            </Button>
          </div>
          {consistency && (
            <div className="mt-4 space-y-2">
              {consistency.warnings.length === 0 ? (
                <Alert tone="green" title="Nie wykryto niespójności" />
              ) : (
                <ul className="space-y-2">
                  {consistency.warnings.map((w, i) => (
                    <li key={i}>
                      <Alert
                        tone={w.severity === "ERROR" ? "red" : "amber"}
                        title={`${w.severity === "ERROR" ? "Błąd" : "Ostrzeżenie"} · ${WARNING_LABEL[w.code] ?? w.code}`}
                      >
                        {w.message}
                      </Alert>
                    </li>
                  ))}
                </ul>
              )}
              <AiComplianceBadge meta={consistency.complianceMeta} />
              <p className="text-xs text-slate-600">
                Kontrola regułowa (bez modelu AI) — {consistency.auto_modified ? "zmieniła" : "nie zmienia"} treści arkusza.
              </p>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}

function ResultBox({ result, fallback }: { result: EvaluationResult | null; fallback: CycleView["cycle"] }) {
  const outcome = result?.outcome ?? fallback.finalResult;
  const level = result?.level ?? fallback.performanceLevel;
  const explanation = result?.explanation ?? fallback.resultExplanation;
  if (!outcome) return null;
  return (
    <Alert tone={outcome === "POSITIVE" ? "green" : "red"} title={`Ocena ${OUTCOME_LABEL[outcome].toLowerCase()}${level ? ` · ${LEVEL_LABEL[level]}` : ""}`}>
      {explanation}
    </Alert>
  );
}
