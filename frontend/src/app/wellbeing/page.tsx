"use client";

import { useState } from "react";
import { Alert, Button, Card, ProblemAlert } from "@/components/ui";
import { api } from "@/lib/api";

const QUESTIONS = [
  { key: "workload", label: "Jak oceniasz swoje obciążenie pracą?", low: "niskie", high: "bardzo wysokie" },
  { key: "energy", label: "Ile masz energii do pracy?", low: "mało", high: "dużo" },
  { key: "support", label: "Na ile czujesz wsparcie przełożonego?", low: "słabo", high: "w pełni" },
] as const;
type Answers = Record<(typeof QUESTIONS)[number]["key"], number>;
const SCALE = [1, 2, 3, 4, 5];

/** Ankieta dobrowolna: bez audytu, bez powiązania z oceną — raportowana wyłącznie zbiorczo (k-anonimizacja). */
export default function WellbeingPage() {
  const [answers, setAnswers] = useState<Answers>({ workload: 3, energy: 3, support: 3 });
  const [wantsConversation, setWantsConversation] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [sent, setSent] = useState(false);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await api("/api/wellbeing", { body: { ...answers, wantsConversation } });
      setSent(true);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold text-gov-text">Dobrostan — ankieta dobrowolna</h1>
      <Alert tone="blue">
        Odpowiedzi nie są powiązane z oceną okresową ani zapisywane w dzienniku audytu. Kadry i DG widzą tylko zbiorcze
        średnie, i to dopiero gdy liczba odpowiedzi osiągnie próg anonimizacji (domyślnie 5).
      </Alert>
      <Card title="Jak się masz w pracy?">
        {sent ? (
          <Alert tone="green" title="Dziękujemy — odpowiedź została zapisana.">
            {wantsConversation ? "Zaznaczono prośbę o rozmowę — będzie widoczna wyłącznie zbiorczo." : null}
          </Alert>
        ) : (
          <div className="space-y-5">
            {QUESTIONS.map((q) => (
              <fieldset key={q.key}>
                <legend className="mb-2 text-sm font-semibold text-gov-text">{q.label}</legend>
                <div className="flex items-center gap-2 text-xs text-slate-700">
                  <span>{q.low}</span>
                  {SCALE.map((v) => (
                    <label key={v} className="flex flex-col items-center gap-1">
                      <input
                        type="radio"
                        name={q.key}
                        className="h-5 w-5 accent-navy-700"
                        checked={answers[q.key] === v}
                        onChange={() => setAnswers((a) => ({ ...a, [q.key]: v }))}
                      />
                      {v}
                    </label>
                  ))}
                  <span>{q.high}</span>
                </div>
              </fieldset>
            ))}
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                className="h-4 w-4 accent-navy-700"
                checked={wantsConversation}
                onChange={(e) => setWantsConversation(e.target.checked)}
              />
              Chcę porozmawiać o obciążeniu lub organizacji pracy
            </label>
            <ProblemAlert error={error} />
            <Button onClick={submit} busy={busy}>
              Wyślij ankietę
            </Button>
          </div>
        )}
      </Card>
    </div>
  );
}
