"use client";

import { useEffect, useState } from "react";
import { Alert, Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner, VisibilityBanner } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate, today, type Tone } from "@/lib/labels";
import type { PreInterviewSurvey } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";
import type { CycleView } from "./context";

type OpenMode = "hidden" | "now" | "scheduled";

/**
 * Ankieta przygotowawcza przed rozmową (/api/cycles/{id}/pre-interview-survey) — niezależna od anonimowej ankiety
 * dobrostanu. Przełożony decyduje, od kiedy oceniany ją widzi, dodaje wstęp, termin odpowiedzi i pytania własne;
 * oceniany odpowiada i wysyła ją przełożonemu.
 */
export function PreInterviewSurveyCard({ view }: { view: CycleView }) {
  const { cycle, isEvaluator, isEmployee, isOfficeHr } = view;
  const base = `/api/cycles/${cycle.id}/pre-interview-survey`;
  const visible = isEvaluator || isEmployee || isOfficeHr;
  const survey = useAsync(() => (visible ? api<PreInterviewSurvey>(base) : Promise.resolve(null)), [cycle.id, visible]);
  const [data, setData] = useState<PreInterviewSurvey | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [question, setQuestion] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const accept = (next: PreInterviewSurvey) => {
    setData(next);
    setAnswers(Object.fromEntries(next.questions.map((q) => [q.id, q.answer ?? ""])));
  };

  useEffect(() => {
    if (survey.data) accept(survey.data);
  }, [survey.data]);

  if (!visible) return null;

  const run = async (key: string, fn: () => Promise<PreInterviewSurvey>, message?: string) => {
    setBusy(key);
    setError(null);
    setNotice(null);
    try {
      accept(await fn());
      if (message) setNotice(message);
      return true;
    } catch (e) {
      setError(e);
      return false;
    } finally {
      setBusy(null);
    }
  };

  const draft = data?.status === "DRAFT" && cycle.status !== "COMPLETED" && cycle.status !== "APPEALED";
  const canAddQuestions = (isEvaluator || isOfficeHr) && draft && data?.allowCustom;
  const employeeCanAnswer = isEmployee && draft && data?.open;

  return (
    <Card title="Ankieta przed rozmową" actions={data && <SurveyStatusBadge survey={data} />}>
      {survey.loading && <Spinner />}
      <ProblemAlert error={survey.error} />
      {data && (
        <div className="space-y-4">
          <VisibilityBanner kind="shared-on-send" />

          {data.canSchedule && <ScheduleForm survey={data} interviewDate={cycle.criteriaInterviewDate ?? cycle.evaluationInterviewDate} busy={busy} run={run} base={base} />}

          {isEmployee && !data.open && (
            <Alert tone="slate" title="Ankieta nie jest jeszcze dostępna">
              {data.opensOn
                ? `Przełożony udostępni ankietę ${formatDate(data.opensOn)}.`
                : "Przełożony zdecyduje, kiedy udostępnić ankietę przed rozmową."}
            </Alert>
          )}

          {data.introMessage && (isEmployee ? data.open : true) && (
            <blockquote className="border-l-4 border-gov-blue bg-gov-light px-3 py-2 text-sm text-gov-text">
              <p className="text-xs font-semibold text-slate-700">Wiadomość od przełożonego</p>
              <p className="whitespace-pre-wrap">{data.introMessage}</p>
            </blockquote>
          )}

          {data.questions.length > 0 && (
            <>
              <p className="text-sm text-slate-700">
                {isEmployee
                  ? `Odpowiedzi widzisz tylko Ty — przełożony zobaczy je po kliknięciu „Wyślij przełożonemu”.${
                      data.answerDeadline ? ` Termin odpowiedzi: ${formatDate(data.answerDeadline)}.` : ""
                    }`
                  : data.status === "SENT"
                    ? `Oceniany wysłał ankietę ${formatDate(data.sentAt)}.`
                    : "Oceniany jeszcze nie wysłał ankiety — odpowiedzi są niewidoczne."}
              </p>
              <ol className="space-y-3">
                {data.questions.map((q, i) => (
                  <li key={q.id} className="rounded border border-slate-200 p-3 text-sm">
                    <div className="flex items-start justify-between gap-2">
                      <span className="font-medium text-gov-text">
                        {i + 1}. {q.text}
                      </span>
                      {q.custom && (
                        <span className="flex shrink-0 items-center gap-2">
                          <Badge tone="blue">pytanie własne</Badge>
                          {canAddQuestions && (
                            <button
                              type="button"
                              className="text-xs text-gov-blue underline"
                              aria-label={`Usuń pytanie ${i + 1}`}
                              onClick={() => void run(`del-${q.id}`, () => api(`${base}/questions/${q.id}`, { method: "DELETE" }))}
                            >
                              usuń
                            </button>
                          )}
                        </span>
                      )}
                    </div>
                    {employeeCanAnswer ? (
                      <textarea
                        aria-label={`Odpowiedź na pytanie ${i + 1}`}
                        className={`${inputClass} mt-2`}
                        rows={2}
                        maxLength={5000}
                        value={answers[q.id] ?? ""}
                        onChange={(e) => setAnswers((a) => ({ ...a, [q.id]: e.target.value }))}
                      />
                    ) : data.answersVisible ? (
                      <p className="mt-1 whitespace-pre-wrap text-slate-800">{q.answer ?? <span className="text-slate-600">— brak odpowiedzi —</span>}</p>
                    ) : null}
                  </li>
                ))}
              </ol>
            </>
          )}

          {canAddQuestions && (
            <div className="flex flex-wrap items-end gap-2">
              <div className="min-w-[16rem] flex-1">
                <Field label="Dodaj pytanie własne" hint="Maks. 10 pytań własnych. Ankieta nie zmienia kryteriów arkusza.">
                  <input className={inputClass} maxLength={500} value={question} onChange={(e) => setQuestion(e.target.value)} />
                </Field>
              </div>
              <Button
                variant="secondary"
                disabled={!question.trim()}
                busy={busy === "add"}
                onClick={() =>
                  void run("add", async () => {
                    const next = await api<PreInterviewSurvey>(`${base}/questions`, { body: { text: question } });
                    setQuestion("");
                    return next;
                  })
                }
              >
                Dodaj
              </Button>
            </div>
          )}

          {employeeCanAnswer && (
            <div className="flex flex-wrap gap-2">
              <Button
                variant="secondary"
                busy={busy === "save"}
                onClick={() => void run("save", () => api(`${base}/answers`, { method: "PUT", body: { answers } }), "Zapisano wersję roboczą odpowiedzi.")}
              >
                Zapisz wersję roboczą
              </Button>
              <Button
                busy={busy === "send"}
                disabled={Object.values(answers).every((a) => !a.trim())}
                onClick={() =>
                  void run(
                    "send",
                    async () => {
                      await api(`${base}/answers`, { method: "PUT", body: { answers } });
                      return api<PreInterviewSurvey>(`${base}/send`, { method: "POST" });
                    },
                    "Ankieta została wysłana przełożonemu.",
                  )
                }
              >
                Wyślij przełożonemu
              </Button>
            </div>
          )}
          <ProblemAlert error={error} />
          {notice && !error && <Alert tone="green">{notice}</Alert>}
        </div>
      )}
    </Card>
  );
}

function SurveyStatusBadge({ survey }: { survey: PreInterviewSurvey }) {
  const [tone, label]: [Tone, string] =
    survey.status === "SENT"
      ? ["green", "wysłana przez ocenianego"]
      : survey.open
        ? ["blue", "udostępniona ocenianemu"]
        : survey.opensOn
          ? ["amber", `zaplanowana od ${formatDate(survey.opensOn)}`]
          : ["slate", "nieudostępniona"];
  return <Badge tone={tone}>{label}</Badge>;
}

/** Panel przełożonego: kiedy ankieta pojawi się u ocenianego, termin odpowiedzi i wiadomość wstępna. */
function ScheduleForm({
  survey,
  interviewDate,
  busy,
  run,
  base,
}: {
  survey: PreInterviewSurvey;
  interviewDate: string | null;
  busy: string | null;
  run: (key: string, fn: () => Promise<PreInterviewSurvey>, message?: string) => Promise<boolean>;
  base: string;
}) {
  const todayIso = today();
  const initialMode: OpenMode = !survey.opensOn ? "hidden" : survey.opensOn <= todayIso ? "now" : "scheduled";
  const [mode, setMode] = useState<OpenMode>(initialMode);
  const [opensOn, setOpensOn] = useState(survey.opensOn && survey.opensOn > todayIso ? survey.opensOn : "");
  const [deadline, setDeadline] = useState(survey.answerDeadline ?? "");
  const [intro, setIntro] = useState(survey.introMessage ?? "");
  const hasAnswersStarted = survey.open && survey.status === "DRAFT";

  const effectiveOpensOn = mode === "hidden" ? null : mode === "now" ? (survey.open && survey.opensOn ? survey.opensOn : todayIso) : opensOn || null;
  const save = (key: string, body: { opensOn: string | null; answerDeadline: string | null; introMessage: string | null }, message: string) =>
    run(key, () => api<PreInterviewSurvey>(`${base}/settings`, { method: "PUT", body }), message);

  return (
    <fieldset className="space-y-3 rounded border-2 border-gov-blue p-3">
      <legend className="px-1 text-sm font-bold text-gov-blue">Ustawienia przełożonego</legend>

      <div className="flex flex-wrap items-center gap-2">
        {survey.open ? (
          <Button
            variant="secondary"
            busy={busy === "close"}
            onClick={() =>
              void save("close", { opensOn: null, answerDeadline: null, introMessage: intro || null }, "Wycofano udostępnienie ankiety.").then((ok) => ok && setMode("hidden"))
            }
          >
            Wycofaj udostępnienie
          </Button>
        ) : (
          <Button
            busy={busy === "open-now"}
            onClick={() =>
              void save(
                "open-now",
                { opensOn: todayIso, answerDeadline: deadline && deadline >= todayIso ? deadline : null, introMessage: intro || null },
                "Ankieta jest teraz widoczna dla ocenianego.",
              ).then((ok) => ok && setMode("now"))
            }
          >
            Włącz ankietę teraz
          </Button>
        )}
        <span className="text-xs text-slate-700">
          {survey.open
            ? `Oceniany widzi ankietę od ${formatDate(survey.opensOn)}.`
            : survey.opensOn
              ? `Ankieta pojawi się u ocenianego ${formatDate(survey.opensOn)}.`
              : "Oceniany nie widzi jeszcze ankiety — możesz spokojnie przygotować pytania."}
        </span>
      </div>
      {hasAnswersStarted && (
        <p className="text-xs text-slate-600">Wycofanie jest możliwe tylko, dopóki oceniany nie zapisał żadnej odpowiedzi.</p>
      )}

      <div role="radiogroup" aria-label="Kiedy udostępnić ankietę ocenianemu" className="space-y-1.5 text-sm">
        <p className="font-semibold text-gov-text">Kiedy udostępnić ankietę ocenianemu?</p>
        {(
          [
            ["hidden", "Jeszcze nie udostępniaj"],
            ["now", "Od razu"],
            ["scheduled", "Od wybranego dnia"],
          ] as const
        ).map(([value, label]) => (
          <label key={value} className="flex items-center gap-2">
            <input type="radio" name={`survey-open-${survey.cycleId}`} className="h-4 w-4 accent-gov-blue" checked={mode === value} onChange={() => setMode(value)} />
            {label}
          </label>
        ))}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {mode === "scheduled" && (
          <Field label="Data udostępnienia" hint={interviewDate ? `Rozmowa zaplanowana na ${formatDate(interviewDate)}.` : undefined}>
            <input type="date" min={todayIso} className={inputClass} value={opensOn} onChange={(e) => setOpensOn(e.target.value)} />
          </Field>
        )}
        {mode !== "hidden" && (
          <Field label="Termin odpowiedzi (opcjonalnie)" hint="Oceniany dostanie przypomnienie z tym terminem.">
            <input
              type="date"
              min={effectiveOpensOn ?? todayIso}
              max={interviewDate ?? undefined}
              className={inputClass}
              value={deadline}
              onChange={(e) => setDeadline(e.target.value)}
            />
          </Field>
        )}
      </div>

      <Field label="Wiadomość wstępna do ocenianego (opcjonalnie)" hint="Np. cel ankiety i to, jak wykorzystasz odpowiedzi podczas rozmowy.">
        <textarea className={inputClass} rows={2} maxLength={2000} value={intro} onChange={(e) => setIntro(e.target.value)} />
      </Field>

      <Button
        variant="secondary"
        busy={busy === "settings"}
        disabled={mode === "scheduled" && !opensOn}
        onClick={() =>
          void save(
            "settings",
            { opensOn: effectiveOpensOn, answerDeadline: mode === "hidden" ? null : deadline || null, introMessage: intro || null },
            "Zapisano ustawienia ankiety.",
          )
        }
      >
        Zapisz ustawienia ankiety
      </Button>
    </fieldset>
  );
}
