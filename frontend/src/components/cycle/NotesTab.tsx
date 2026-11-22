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
  Spinner,
  Stat,
} from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api, coreAi } from "@/lib/api";
import { formatDate } from "@/lib/labels";
import type { HrFacts, HrPartnerResponse, Note } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";
import type { CycleView } from "./context";
import { PrivateDiary } from "./PrivateDiary";

const ACTIVE = ["DRAFT_CRITERIA", "CRITERIA_CONFIRMED", "OBSERVATION", "EVALUATION_DRAFT", "AWAITING_ACK"];
/** RETENTION_GRACE_DAYS w backend-core (domyślnie 30) — notatki robocze zamkniętych cykli są usuwane po tym czasie. */
const WORKING_NOTES_GRACE_DAYS = 30;

export function NotesTab({ view }: { view: CycleView }) {
  const [section, setSection] = useState<"notes" | "diary">("notes");
  if (!view.isEmployee) return <WorkingNotes view={view} />;
  return (
    <div className="space-y-4">
      <div role="tablist" aria-label="Notatki" className="flex flex-wrap gap-1 border-b border-slate-200">
        {(
          [
            ["notes", "Notatki przełożonego"],
            ["diary", "Mój prywatny dziennik"],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            role="tab"
            id={`notes-tab-${id}`}
            aria-selected={section === id}
            aria-controls={`notes-panel-${id}`}
            onClick={() => setSection(id)}
            className={`-mb-px border-b-4 px-3 py-2 text-sm font-bold focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue ${
              section === id ? "border-gov-blue text-gov-blue" : "border-transparent text-gov-text hover:text-gov-blue"
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`notes-panel-${section}`} aria-labelledby={`notes-tab-${section}`}>
        {section === "notes" ? <WorkingNotes view={view} /> : <PrivateDiary cycleId={view.cycle.id} />}
      </div>
    </div>
  );
}

function WorkingNotes({ view }: { view: CycleView }) {
  const { cycle, notes, notesError, isEvaluator, isEmployee, refresh } = view;
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
          {!cycle.workingNotesPurgedAt && (
            <div className="mb-3">
              <Alert tone="blue" title="Notatki robocze są tymczasowe — bez „czarnych teczek”">
                Notatki robocze są automatycznie usuwane {WORKING_NOTES_GRACE_DAYS} dni po zamknięciu cyklu. Do akt osobowych trafia
                wyłącznie arkusz oceny (część B); notatki nie wchodzą też do paczki archiwalnej EZD.
                {cycle.legalHold ? " Spór przed sądem pracy wstrzymuje usunięcie do jego zakończenia." : ""}
              </Alert>
            </div>
          )}
          {cycle.workingNotesPurgedAt && (
            <div className="mb-3">
              <Alert tone="slate">
                Notatki robocze usunięto {formatDate(cycle.workingNotesPurgedAt.slice(0, 10))} po zamknięciu cyklu (dane pomocnicze — RODO).
                Arkusz oceny pozostaje w części B akt osobowych.
              </Alert>
            </div>
          )}
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
                Udostępnij ocenianemu po zakończeniu oceny
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
        {isEmployee ? (
          <Alert tone="slate" title="Wirtualny doradca HR">
            Moduł wspiera przełożonego i kadry — oceniany nie widzi analiz opartych na roboczych notatkach.
          </Alert>
        ) : (
          <HrPartnerPanel
            cycleId={cycle.id}
            notes={(notes ?? []).map((n) => n.content)}
            notesCount={notes?.length ?? 0}
            disabled={!!notesError}
          />
        )}
      </div>
    </div>
  );
}

function HrPartnerPanel({ cycleId, notes, notesCount, disabled }: { cycleId: number; notes: string[]; notesCount: number; disabled: boolean }) {
  const facts = useAsync(() => api<HrFacts>(`/api/cycles/${cycleId}/hr-facts`), [cycleId, notesCount]);
  const [result, setResult] = useState<HrPartnerResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const analyze = async (f: HrFacts) => {
    setBusy(true);
    setError(null);
    try {
      setResult(
        await api<HrPartnerResponse>(coreAi("hr-partner", cycleId), {
          body: { notes, workload_signals: workloadSignals(f) },
        }),
      );
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Wirtualny doradca HR" actions={result && <ModeBadge mode={result.mode} />}>
      <p className="mb-3 text-xs text-slate-600">Fakty policzone z bazy (nie szacowane przez AI) — przekazywane do analizy.</p>
      {facts.loading && !facts.data && <Spinner label="Liczenie faktów…" />}
      <ProblemAlert error={facts.error} />
      {facts.data && (
        <>
          <div className="grid grid-cols-3 gap-2">
            <Stat label="Dni nieobecności" value={facts.data.absenceDays} tone={facts.data.absenceDays > 0 ? "amber" : "slate"} />
            <Stat label="Zmiany zakresu" value={facts.data.scopeChangesCount} tone={facts.data.scopeChangesCount > 0 ? "amber" : "slate"} />
            <Stat
              label="Notatki o przeciążeniu"
              value={facts.data.overloadNotesCount}
              hint={`z ${facts.data.totalNotesCount}`}
              tone={facts.data.overloadNotesCount > 0 ? "red" : "slate"}
            />
          </div>
          <p className="mt-2 text-xs text-slate-600">
            Ostatnia notatka: {facts.data.daysSinceLastNote === null ? "brak" : `${facts.data.daysSinceLastNote} dni temu`}
            {facts.data.overdueDays > 0 ? ` · po terminie ${facts.data.overdueDays} dni` : ""}
          </p>
          <div className="mt-3">
            <Button variant="ai" onClick={() => analyze(facts.data!)} busy={busy} disabled={disabled}>
              Analizuj z wirtualnym doradcą HR
            </Button>
          </div>
        </>
      )}
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
            <AiComplianceBadge meta={result.complianceMeta} />
            <AiDisclaimer text={result.ai_disclaimer} />
          </>
        )}
      </div>
    </Card>
  );
}

function workloadSignals(f: HrFacts): Record<string, number> {
  const signals: Record<string, number> = {
    absence_days: f.absenceDays,
    scope_changes: f.scopeChangesCount,
    overload_notes: f.overloadNotesCount,
    total_notes: f.totalNotesCount,
    overdue_days: f.overdueDays,
  };
  if (f.daysSinceLastNote !== null) signals.days_since_last_note = f.daysSinceLastNote;
  return signals;
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
