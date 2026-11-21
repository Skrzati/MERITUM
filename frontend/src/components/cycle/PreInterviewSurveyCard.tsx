"use client";

import { useEffect, useState } from "react";
import { Alert, Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/labels";
import type { PreInterviewSurvey } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";
import type { CycleView } from "./context";

/**
 * Ankieta przygotowawcza przed rozmową (/api/cycles/{id}/pre-interview-survey) — niezależna od anonimowej ankiety
 * dobrostanu. Przełożony i kadry dodają pytania własne, oceniany odpowiada i wysyła ją przełożonemu.
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
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!survey.data) return;
    setData(survey.data);
    setAnswers(Object.fromEntries(survey.data.questions.map((q) => [q.id, q.answer ?? ""])));
  }, [survey.data]);

  if (!visible) return null;

  const run = async (key: string, fn: () => Promise<PreInterviewSurvey>) => {
    setBusy(key);
    setError(null);
    setSaved(false);
    try {
      const next = await fn();
      setData(next);
      setAnswers(Object.fromEntries(next.questions.map((q) => [q.id, q.answer ?? ""])));
      if (key === "save") setSaved(true);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const draft = data?.status === "DRAFT" && cycle.status !== "COMPLETED" && cycle.status !== "APPEALED";
  const canAddQuestions = (isEvaluator || isOfficeHr) && draft && data?.allowCustom;

  return (
    <Card
      title="Ankieta przed rozmową"
      actions={data && <Badge tone={data.status === "SENT" ? "green" : "slate"}>{data.status === "SENT" ? "wysłana" : "szkic"}</Badge>}
    >
      {survey.loading && <Spinner />}
      <ProblemAlert error={survey.error} />
      {data && (
        <div className="space-y-4">
          <p className="text-sm text-slate-700">
            {isEmployee
              ? "Odpowiedzi widzisz tylko Ty — przełożony zobaczy je po kliknięciu „Wyślij przełożonemu”."
              : data.status === "SENT"
                ? `Oceniany wysłał ankietę ${formatDate(data.sentAt)}.`
                : "Oceniany jeszcze nie wysłał ankiety — odpowiedzi są niewidoczne."}{" "}
            Ankieta jest materiałem pomocniczym — nie trafia do arkusza ani do akt osobowych.
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
                          onClick={() => void run(`del-${q.id}`, () => api(`${base}/questions/${q.id}`, { method: "DELETE" }))}
                        >
                          usuń
                        </button>
                      )}
                    </span>
                  )}
                </div>
                {isEmployee && draft ? (
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

          {canAddQuestions && (
            <div className="flex flex-wrap items-end gap-2">
              <div className="min-w-[16rem] flex-1">
                <Field label="Dodaj pytanie własne" hint="Pytania własne są dozwolone w ankiecie (arkusz urzędowy v1 nadal bez kryteriów własnych).">
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

          {isEmployee && draft && (
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" busy={busy === "save"} onClick={() => void run("save", () => api(`${base}/answers`, { method: "PUT", body: { answers } }))}>
                Zapisz szkic
              </Button>
              <Button
                busy={busy === "send"}
                disabled={Object.values(answers).every((a) => !a.trim())}
                onClick={() =>
                  void run("send", async () => {
                    await api(`${base}/answers`, { method: "PUT", body: { answers } });
                    return api<PreInterviewSurvey>(`${base}/send`, { method: "POST" });
                  })
                }
              >
                Wyślij przełożonemu
              </Button>
            </div>
          )}
          <ProblemAlert error={error} />
          {saved && !error && <Alert tone="green">Zapisano szkic.</Alert>}
        </div>
      )}
    </Card>
  );
}
