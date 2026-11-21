"use client";

import { useState } from "react";
import { AiDisclaimer, Alert, Badge, Button, Card, Field, inputClass, MaskedPrompt, ModeBadge, ProblemAlert } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/labels";
import type { HrPartnerResponse, Note } from "@/lib/types";
import type { CycleView } from "./context";

const ACTIVE = ["DRAFT_CRITERIA", "CRITERIA_CONFIRMED", "OBSERVATION", "EVALUATION_DRAFT", "AWAITING_ACK"];

export function NotesTab({ view }: { view: CycleView }) {
  const { cycle, notes, notesError, isEvaluator, refresh } = view;
  const { userName } = useUser();
  const [content, setContent] = useState("");
  const [shared, setShared] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const add = async () => {
    setBusy(true);
    setError(null);
    try {
      await api<Note>(`/api/cycles/${cycle.id}/notes`, { body: { content, isSharedAfterEval: shared } });
      setContent("");
      await refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-5">
      <div className="space-y-6 lg:col-span-3">
        <Card title="Oś czasu notatek">
          {notesError ? (
            <Alert tone="amber">Notatki widzi wyłącznie oceniający i oceniany.</Alert>
          ) : notes?.length ? (
            <ol className="relative space-y-4 border-l-2 border-slate-200 pl-5">
              {notes.map((n) => (
                <li key={n.id}>
                  <span aria-hidden className="absolute -left-[7px] mt-1.5 h-3 w-3 rounded-full bg-navy-600" />
                  <p className="text-xs text-slate-600">
                    {formatDate(n.createdAt)} · {userName(n.authorId)}{" "}
                    {n.isSharedAfterEval ? <Badge tone="blue">udostępniana po ocenie</Badge> : <Badge>prywatna</Badge>}
                  </p>
                  <p className="mt-1 text-sm text-slate-900">{n.content}</p>
                </li>
              ))}
            </ol>
          ) : (
            <p className="text-sm text-slate-700">Brak notatek.</p>
          )}
        </Card>

        {isEvaluator && ACTIVE.includes(cycle.status) && (
          <Card title="Dodaj notatkę">
            <div className="space-y-3">
              <Field label="Treść notatki (fakty, daty, zdarzenia)">
                <textarea className={inputClass} rows={3} value={content} onChange={(e) => setContent(e.target.value)} />
              </Field>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" className="h-4 w-4 accent-navy-700" checked={shared} onChange={(e) => setShared(e.target.checked)} />
                Udostępnij ocenianemu po zakończeniu oceny (<code>isSharedAfterEval</code>)
              </label>
              <ProblemAlert error={error} />
              <Button onClick={add} busy={busy} disabled={!content.trim()}>
                Dodaj notatkę
              </Button>
            </div>
          </Card>
        )}
      </div>

      <div className="lg:col-span-2">
        <HrPartnerPanel notes={(notes ?? []).map((n) => n.content)} disabled={!!notesError} />
      </div>
    </div>
  );
}

function HrPartnerPanel({ notes, disabled }: { notes: string[]; disabled: boolean }) {
  const [overtime, setOvertime] = useState("22");
  const [projects, setProjects] = useState("3");
  const [trainings, setTrainings] = useState("1");
  const [result, setResult] = useState<HrPartnerResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const analyze = async () => {
    setBusy(true);
    setError(null);
    try {
      setResult(
        await api<HrPartnerResponse>("/api/ai/hr-partner", {
          body: {
            notes,
            workload_signals: {
              overtime_hours: Number(overtime) || 0,
              concurrent_projects: Number(projects) || 0,
              missed_trainings: Number(trainings) || 0,
            },
          },
        }),
      );
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Wirtualny HR Partner" actions={result && <ModeBadge mode={result.mode} />}>
      <div className="grid grid-cols-3 gap-2">
        <Field label="Nadgodziny / mies.">
          <input type="number" min={0} className={inputClass} value={overtime} onChange={(e) => setOvertime(e.target.value)} />
        </Field>
        <Field label="Projekty równolegle">
          <input type="number" min={0} className={inputClass} value={projects} onChange={(e) => setProjects(e.target.value)} />
        </Field>
        <Field label="Pominięte szkolenia">
          <input type="number" min={0} className={inputClass} value={trainings} onChange={(e) => setTrainings(e.target.value)} />
        </Field>
      </div>
      <div className="mt-3">
        <Button variant="ai" onClick={analyze} busy={busy} disabled={disabled}>
          Analizuj z Wirtualnym HR Partnerem
        </Button>
      </div>
      <div className="mt-4 space-y-4">
        <ProblemAlert error={error} />
        {result && (
          <>
            {result.burnout_risk_signal ? (
              <Alert tone="amber" title="⚠️ Sygnał ryzyka przeciążenia">
                Warto porozmawiać o obciążeniu. To sygnał organizacyjny, nie diagnoza.
              </Alert>
            ) : (
              <Alert tone="green" title="Brak sygnału przeciążenia" />
            )}
            <Section title="Jak zacząć rozmowę" items={result.conversation_starters} />
            <Section title="Rekomendacje organizacyjne" items={result.organizational_suggestions} />
            <div>
              <h3 className="text-sm font-semibold text-navy-900">Moment docenienia</h3>
              <p className="mt-1 text-sm text-slate-800">{result.appreciation_moment}</p>
            </div>
            <MaskedPrompt prompt={result.masked_prompt} />
            <AiDisclaimer text={result.ai_disclaimer} />
          </>
        )}
      </div>
    </Card>
  );
}

function Section({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <h3 className="text-sm font-semibold text-navy-900">{title}</h3>
      <ul className="mt-1 list-disc space-y-1 pl-5 text-sm text-slate-800">
        {items.map((i) => (
          <li key={i}>{i}</li>
        ))}
      </ul>
    </div>
  );
}
