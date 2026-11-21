"use client";

import { useState } from "react";
import { Alert, Button, Card, ProblemAlert } from "@/components/ui";
import { api } from "@/lib/api";

const QUESTIONS = [
  {
    key: "workload",
    label: "Obciążenie pracą",
    question: "Jak oceniasz swoje obciążenie pracą w ostatnim miesiącu?",
    scale: ["bardzo niskie", "niskie", "umiarkowane", "wysokie", "bardzo wysokie"],
  },
  {
    key: "energy",
    label: "Poziom energii",
    question: "Ile masz energii do wykonywania obowiązków?",
    scale: ["bardzo mało", "mało", "średnio", "dużo", "bardzo dużo"],
  },
  {
    key: "support",
    label: "Wsparcie przełożonego",
    question: "Na ile czujesz wsparcie bezpośredniego przełożonego?",
    scale: ["brak", "słabe", "częściowe", "dobre", "pełne"],
  },
] as const;
type Answers = Record<(typeof QUESTIONS)[number]["key"], number>;
const NEUTRAL = 3;

/** Ankieta dobrowolna: bez audytu, bez powiązania z oceną — raportowana wyłącznie zbiorczo (k-anonimizacja). */
export default function WellbeingPage() {
  const [answers, setAnswers] = useState<Answers>({ workload: NEUTRAL, energy: NEUTRAL, support: NEUTRAL });
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
    <div className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-2xl font-bold text-gov-text">Dobrostan — ankieta dobrowolna</h1>

      <section aria-labelledby="rodo-title" className="rounded border-2 border-gov-blue bg-white p-4">
        <h2 id="rodo-title" className="text-base font-bold text-gov-blue">
          Informacja o przetwarzaniu danych (RODO art. 13)
        </h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-gov-text">
          <li>
            Udział jest <strong>dobrowolny</strong> — brak odpowiedzi nie ma żadnych konsekwencji.
          </li>
          <li>
            Ankieta jest <strong>niezależna od oceny okresowej</strong>: nie jest powiązana z cyklem oceny, nie trafia do arkusza ani
            do dziennika audytu.
          </li>
          <li>
            Kadry i Dyrektor Generalny widzą <strong>wyłącznie agregaty</strong> — średnie pojawiają się dopiero przy co najmniej
            k = 5 odpowiedziach (k-anonimizacja po stronie serwera).
          </li>
          <li>
            Ankieta jest <strong>technicznie anonimowa</strong>: odpowiedź zapisuje się bez Twojego identyfikatora, z datą (bez
            godziny) — nie da się jej przypisać do osoby ani wydać w eksporcie z art. 15 RODO.
          </li>
        </ul>
      </section>

      <Card title="Jak się masz w pracy?">
        {sent ? (
          <Alert tone="green" title="Dziękujemy — odpowiedź została zapisana.">
            {wantsConversation ? "Prośba o rozmowę będzie widoczna dla kadr wyłącznie zbiorczo." : null}
          </Alert>
        ) : (
          <div className="space-y-6">
            {QUESTIONS.map((q) => (
              <fieldset key={q.key}>
                <legend className="text-sm font-bold text-gov-text">{q.label}</legend>
                <p className="mb-2 text-sm text-slate-700">{q.question}</p>
                <div className="grid grid-cols-5 gap-2">
                  {q.scale.map((label, i) => {
                    const value = i + 1;
                    const checked = answers[q.key] === value;
                    return (
                      <label
                        key={value}
                        className={`flex cursor-pointer flex-col items-center gap-1 rounded border p-2 text-center text-xs ${
                          checked ? "border-gov-blue bg-gov-light font-semibold text-gov-hover" : "border-slate-300 hover:bg-gov-section"
                        }`}
                      >
                        <input
                          type="radio"
                          name={q.key}
                          className="h-4 w-4 accent-navy-700"
                          checked={checked}
                          onChange={() => setAnswers((a) => ({ ...a, [q.key]: value }))}
                        />
                        <span className="text-base font-bold">{value}</span>
                        <span>{label}</span>
                      </label>
                    );
                  })}
                </div>
              </fieldset>
            ))}
            <label className="flex items-start gap-2 rounded border border-slate-300 p-3 text-sm">
              <input
                type="checkbox"
                className="mt-0.5 h-4 w-4 accent-navy-700"
                checked={wantsConversation}
                onChange={(e) => setWantsConversation(e.target.checked)}
              />
              <span>
                <span className="font-semibold">Chcę porozmawiać o obciążeniu lub organizacji pracy</span>
                <span className="block text-xs text-slate-600">Kadry widzą tylko łączną liczbę próśb (od progu k = 5), bez wskazania osób.</span>
              </span>
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
