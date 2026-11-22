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
} from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api, coreAi, download } from "@/lib/api";
import { DIFFICULT_SCENARIO_LABEL, formatDate, INTERVIEW_LEGAL_STATUS } from "@/lib/labels";
import type {
  AppreciationResponse,
  DevelopmentEntry,
  DifficultScenario,
  FeedbackCheckin,
  InterviewPrepResponse,
  InterviewStage,
  SelfPrep,
} from "@/lib/types";
import { useAsync } from "@/lib/useAsync";
import type { CycleView } from "./context";
import { PreInterviewSurveyCard } from "./PreInterviewSurveyCard";

const STAGES: { id: InterviewStage; label: string; legalStatus: string }[] = [
  { id: "INITIAL", label: "Rozmowa rozpoczynająca (§ 5 ust. 1)", legalStatus: INTERVIEW_LEGAL_STATUS.mandatory },
  { id: "EVALUATION", label: "Rozmowa oceniająca (§ 10)", legalStatus: INTERVIEW_LEGAL_STATUS.mandatory },
  { id: "FEEDBACK", label: "Rozmowa bieżąca — informacja zwrotna (FUKO)", legalStatus: INTERVIEW_LEGAL_STATUS.recommended },
];
const SCENARIOS = Object.keys(DIFFICULT_SCENARIO_LABEL) as DifficultScenario[];
const COPY_FEEDBACK_MS = 2500;

/** Przygotowanie do rozmowy przez audytowane /api/core-ai/interview-prep. */
export async function fetchInterviewPrep(
  view: CycleView,
  employeeName: string,
  stage: InterviewStage,
  difficultScenario: DifficultScenario | null = null,
) {
  const { cycle, notes } = view;
  return api<InterviewPrepResponse>(coreAi("interview-prep", cycle.id), {
    body: {
      employee_name: employeeName,
      sheet_type: cycle.sheetType,
      stage,
      criteria_scores: (cycle.criteria ?? []).map((c) => ({ code: c.code, name: c.name, score: cycle.scores?.[c.code] ?? null })),
      notes: (notes ?? []).map((n) => n.content),
      difficultScenario,
    },
  });
}

export function InterviewTab({ view }: { view: CycleView }) {
  const { cycle, isEvaluator } = view;
  const { userName } = useUser();
  const [stage, setStage] = useState<InterviewStage>(cycle.status === "DRAFT_CRITERIA" ? "INITIAL" : "EVALUATION");
  const [scenario, setScenario] = useState<DifficultScenario | "">("");
  const [prep, setPrep] = useState<InterviewPrepResponse | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [copied, setCopied] = useState<string | null>(null);

  const icsStage = cycle.status === "DRAFT_CRITERIA" ? "rozpoczynającą (§ 5 ust. 1)" : "oceniającą (§ 10)";
  const interviewDate = cycle.status === "DRAFT_CRITERIA" ? cycle.criteriaInterviewDate : cycle.evaluationInterviewDate;

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

  const prepare = () =>
    run("prep", async () => setPrep(await fetchInterviewPrep(view, userName(cycle.employeeId), stage, scenario || null)));
  const ics = () => run("ics", () => download(`/api/cycles/${cycle.id}/interview.ics`, `rozmowa-cykl-${cycle.id}.ics`));
  const copy = async (criterion: string, actions: string[]) => {
    try {
      await navigator.clipboard.writeText(`${criterion}: ${actions.join("; ")}`);
      setCopied(criterion);
      setTimeout(() => setCopied(null), COPY_FEEDBACK_MS);
    } catch (e) {
      setError(e);
    }
  };

  return (
    <div className="grid gap-6 xl:grid-cols-5">
      <div className="space-y-6 xl:col-span-3">
        <Card title="Przygotowanie do rozmowy" actions={prep && <ModeBadge mode={prep.mode} />}>
          <div role="radiogroup" aria-label="Rodzaj rozmowy" className="flex flex-wrap gap-2">
            {STAGES.map((s) => (
              <button
                key={s.id}
                type="button"
                role="radio"
                aria-checked={stage === s.id}
                onClick={() => {
                  setStage(s.id);
                  setPrep(null);
                }}
                className={`rounded-md px-3 py-1.5 text-sm font-medium ring-1 ring-inset focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-600 ${
                  stage === s.id ? "bg-navy-700 text-white ring-navy-700" : "bg-white text-navy-800 ring-slate-300 hover:bg-slate-50"
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>
          <p className="mt-2 text-xs font-semibold text-slate-700">
            Status prawny: {STAGES.find((s) => s.id === stage)?.legalStatus}
          </p>
          <div className="mt-3 max-w-md">
            <Field label="Trudna rozmowa (opcjonalnie)" hint="Dodaje kartę: struktura rozmowy, pytania i granice roli przełożonego.">
              <select
                className={inputClass}
                value={scenario}
                onChange={(e) => {
                  setScenario(e.target.value as DifficultScenario | "");
                  setPrep(null);
                }}
              >
                <option value="">brak — zwykła rozmowa</option>
                {SCENARIOS.map((sc) => (
                  <option key={sc} value={sc}>
                    {DIFFICULT_SCENARIO_LABEL[sc]}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <div className="mt-4">
            <Button variant="ai" onClick={prepare} busy={busy === "prep"} disabled={!isEvaluator}>
              AI: Przygotuj rozmowę
            </Button>
            {!isEvaluator && <p className="mt-1 text-xs text-slate-600">Przygotowanie rozmowy jest dostępne dla oceniającego.</p>}
          </div>
          <div className="mt-4">
            <ProblemAlert error={error} />
          </div>
          {prep && (
            <div className="mt-4 space-y-5">
              <div>
                <h3 className="text-sm font-semibold text-navy-900">Cel rozmowy</h3>
                <p className="mt-1 text-sm text-slate-800">{prep.goal}</p>
              </div>
              <div>
                <h3 className="text-sm font-semibold text-navy-900">Agenda</h3>
                <ol className="mt-2 space-y-3">
                  {prep.agenda.map((item, i) => (
                    <li key={`${item.topic}-${i}`} className="rounded-md border border-slate-200 p-3">
                      <p className="text-sm font-medium text-slate-900">
                        {i + 1}. {item.topic}
                        {item.legal_basis && <span className="ml-2 text-xs font-normal text-slate-600">({item.legal_basis})</span>}
                      </p>
                      <ul className="mt-1 list-disc space-y-0.5 pl-5 text-sm text-slate-800">
                        {item.questions.map((q) => (
                          <li key={q}>{q}</li>
                        ))}
                      </ul>
                    </li>
                  ))}
                </ol>
              </div>
              {prep.difficult_situations.length > 0 && (
                <div>
                  <h3 className="text-sm font-semibold text-navy-900">Trudne sytuacje</h3>
                  <dl className="mt-2 space-y-2 text-sm">
                    {prep.difficult_situations.map((s) => (
                      <div key={s.situation} className="rounded bg-amber-50 p-2 ring-1 ring-inset ring-amber-200">
                        <dt className="font-medium text-amber-950">{s.situation}</dt>
                        <dd className="mt-0.5 text-slate-800">{s.tip}</dd>
                      </div>
                    ))}
                  </dl>
                </div>
              )}
              {prep.difficult_conversation && <DifficultConversationCard conversation={prep.difficult_conversation} />}
              <MaskedPrompt prompt={prep.masked_prompt} />
              <AiComplianceBadge meta={prep.complianceMeta} />
              <AiDisclaimer text={prep.ai_disclaimer} />
            </div>
          )}
        </Card>
        {isEvaluator && <AppreciationCard view={view} employeeName={userName(cycle.employeeId)} />}
      </div>

      <div className="space-y-6 xl:col-span-2">
        <PreInterviewSurveyCard view={view} />
        <SelfPrepCard view={view} />
        <CheckinsCard view={view} />

        <Card title="Zaproszenie do kalendarza">
          <p className="text-sm text-slate-700">
            Plik .ics na rozmowę <strong>{icsStage}</strong> — termin: {formatDate(interviewDate)}
            {interviewDate ? "" : " (nie ustalono — w zaproszeniu termin ustawowy)"}.
          </p>
          <div className="mt-3">
            <Button variant="secondary" onClick={ics} busy={busy === "ics"}>
              📅 Pobierz zaproszenie .ics
            </Button>
          </div>
        </Card>

        <Card title="Katalog działań rozwojowych (IPRZ)">
          {prep?.development_catalog.length ? (
            <DevelopmentCatalog
              entries={prep.development_catalog}
              actionLabel={(c) => (copied === c ? "✓ Skopiowano" : "Kopiuj do IPRZ")}
              onPick={copy}
            />
          ) : (
            <Alert tone="slate">Przygotuj rozmowę, aby zobaczyć propozycje działań rozwojowych dla kryteriów.</Alert>
          )}
          <p className="mt-3 text-xs text-slate-600">
            Propozycje do wyboru i edycji przez oceniającego — wklej je w polu IPRZ w zakładce „Ocena”.
          </p>
        </Card>
      </div>
    </div>
  );
}

export function DevelopmentCatalog({
  entries,
  actionLabel,
  onPick,
}: {
  entries: DevelopmentEntry[];
  actionLabel: (criterion: string) => string;
  onPick: (criterion: string, actions: string[]) => void;
}) {
  return (
    <ul className="space-y-3">
      {entries.map((d) => (
        <li key={d.criterion} className="rounded-md border border-slate-200 p-3 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="font-medium text-navy-900">{d.criterion}</p>
            {d.estimatedCostPln === 0 && <Badge tone="green">Koszt: 0 zł (działania wewnętrzne)</Badge>}
          </div>
          <dl className="mt-2 space-y-2">
            {(
              [
                ["70% — nauka w pracy", d.onTheJob70],
                ["20% — nauka od innych", d.social20],
                ["10% — formy formalne", d.formal10],
              ] as const
            ).map(([label, items]) =>
              items.length ? (
                <div key={label}>
                  <dt className="text-xs font-semibold uppercase tracking-wide text-slate-700">{label}</dt>
                  <dd>
                    <ul className="list-disc space-y-0.5 pl-5 text-slate-800">
                      {items.map((a) => (
                        <li key={a}>{a}</li>
                      ))}
                    </ul>
                  </dd>
                </div>
              ) : null,
            )}
          </dl>
          <Button variant="secondary" className="mt-2" onClick={() => onPick(d.criterion, d.actions)}>
            {actionLabel(d.criterion)}
          </Button>
        </li>
      ))}
    </ul>
  );
}

/** Rozmowy bieżące (informacja zwrotna, standardy ZZL): zapis — oceniający/zastępca; oceniany widzi udostępnione. */
function CheckinsCard({ view }: { view: CycleView }) {
  const { cycle, isEvaluator, isEmployee } = view;
  const list = useAsync(
    () => (isEvaluator || isEmployee ? api<FeedbackCheckin[]>(`/api/cycles/${cycle.id}/checkins`) : Promise.resolve([])),
    [cycle.id],
  );
  const [topic, setTopic] = useState("");
  const [summary, setSummary] = useState("");
  const [nextSteps, setNextSteps] = useState("");
  const [shared, setShared] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const active = !["COMPLETED", "APPEALED"].includes(cycle.status);

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/cycles/${cycle.id}/checkins`, { body: { topic, summary, nextSteps: nextSteps || null, sharedWithEmployee: shared } });
      setTopic("");
      setSummary("");
      setNextSteps("");
      await list.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  if (!isEvaluator && !isEmployee) return null;
  return (
    <Card title="Rozmowy bieżące (informacja zwrotna)" actions={<Badge tone="slate">nieobowiązkowe</Badge>}>
      <p className="mb-3 text-xs text-slate-700">{INTERVIEW_LEGAL_STATUS.recommended}</p>
      {list.data && list.data.length > 0 ? (
        <ul className="mb-3 space-y-2 text-sm">
          {list.data.map((c) => (
            <li key={c.id} className="rounded border border-slate-200 p-2">
              <p className="font-semibold">
                {formatDate(c.conductedOn)} · {c.topic} {!c.sharedWithEmployee && <span className="text-xs text-slate-600">(robocze)</span>}
              </p>
              <p>{c.summary}</p>
              {c.nextSteps && <p className="text-xs text-slate-700">Kolejny krok: {c.nextSteps}</p>}
            </li>
          ))}
        </ul>
      ) : (
        <p className="mb-3 text-sm text-slate-700">Brak zapisanych rozmów.</p>
      )}
      {isEvaluator && active && (
        <div className="space-y-2">
          <Field label="Temat">
            <input className={inputClass} value={topic} onChange={(e) => setTopic(e.target.value)} />
          </Field>
          <Field label="Podsumowanie (fakty, docenienie, ustalenia)">
            <textarea className={inputClass} rows={2} value={summary} onChange={(e) => setSummary(e.target.value)} />
          </Field>
          <Field label="Kolejny krok">
            <input className={inputClass} value={nextSteps} onChange={(e) => setNextSteps(e.target.value)} />
          </Field>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="h-4 w-4 accent-navy-700" checked={shared} onChange={(e) => setShared(e.target.checked)} />
            Udostępnij ocenianemu
          </label>
          <Button onClick={save} busy={busy} disabled={!topic.trim() || !summary.trim()}>
            Zapisz rozmowę bieżącą
          </Button>
          <p className="text-xs text-slate-600">Zapisana rozmowa bieżąca resetuje kwartalne przypomnienie (90 dni).</p>
        </div>
      )}
      <ProblemAlert error={error} />
    </Card>
  );
}

/** „Moje przygotowanie do rozmowy” — edytuje oceniany; przełożony widzi treść dopiero po udostępnieniu. */
function SelfPrepCard({ view }: { view: CycleView }) {
  const { cycle, isEvaluator, isEmployee } = view;
  const prep = useAsync(
    () => (isEvaluator || isEmployee ? api<SelfPrep>(`/api/cycles/${cycle.id}/self-prep`) : Promise.resolve(null)),
    [cycle.id],
  );
  if (!isEvaluator && !isEmployee) return null;
  return (
    <Card title={isEmployee ? "Moje przygotowanie do rozmowy" : "Przygotowanie ocenianego"}>
      {prep.data && (isEmployee ? <SelfPrepForm cycleId={cycle.id} initial={prep.data} /> : <SelfPrepPreview prep={prep.data} />)}
      <ProblemAlert error={prep.error} />
    </Card>
  );
}

function SelfPrepPreview({ prep }: { prep: SelfPrep }) {
  if (!prep.sharedWithEvaluator) return <p className="text-sm text-slate-700">Oceniany nie udostępnił jeszcze swojego przygotowania.</p>;
  return (
    <dl className="space-y-2 text-sm">
      {[
        ["Osiągnięcia", prep.achievements],
        ["Trudności", prep.challenges],
        ["Potrzeby rozwojowe", prep.developmentNeeds],
      ].map(([label, value]) => (
        <div key={label}>
          <dt className="font-semibold">{label}</dt>
          <dd className="whitespace-pre-wrap">{value || "—"}</dd>
        </div>
      ))}
    </dl>
  );
}

function SelfPrepForm({ cycleId, initial }: { cycleId: number; initial: SelfPrep }) {
  const [form, setForm] = useState({
    achievements: initial.achievements ?? "",
    challenges: initial.challenges ?? "",
    developmentNeeds: initial.developmentNeeds ?? "",
    sharedWithEvaluator: initial.sharedWithEvaluator,
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);

  const save = async () => {
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      await api(`/api/cycles/${cycleId}/self-prep`, { method: "PUT", body: form });
      setSaved(true);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-2">
      {(
        [
          ["achievements", "Moje osiągnięcia w okresie oceny"],
          ["challenges", "Trudności i czynniki niezależne ode mnie"],
          ["developmentNeeds", "Czego potrzebuję, aby się rozwijać"],
        ] as const
      ).map(([key, label]) => (
        <Field key={key} label={label}>
          <textarea className={inputClass} rows={2} value={form[key]} onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))} />
        </Field>
      ))}
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          className="h-4 w-4 accent-navy-700"
          checked={form.sharedWithEvaluator}
          onChange={(e) => setForm((f) => ({ ...f, sharedWithEvaluator: e.target.checked }))}
        />
        Udostępnij przełożonemu przed rozmową
      </label>
      <Button onClick={save} busy={busy}>
        Zapisz
      </Button>
      {saved && <Alert tone="green">Zapisano{form.sharedWithEvaluator ? " i udostępniono przełożonemu" : " (prywatnie)"}.</Alert>}
      <ProblemAlert error={error} />
    </div>
  );
}

function DifficultConversationCard({ conversation }: { conversation: NonNullable<InterviewPrepResponse["difficult_conversation"]> }) {
  return (
    <section aria-label="Karta trudnej rozmowy" className="rounded border-2 border-dashed border-amber-500 p-3 text-sm">
      <h3 className="font-semibold text-navy-900">Trudna rozmowa: {conversation.title}</h3>
      <div className="mt-2 grid gap-3 md:grid-cols-3">
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-700">Struktura rozmowy</h4>
          <ol className="mt-1 list-decimal space-y-0.5 pl-5">
            {conversation.structure.map((x) => (
              <li key={x}>{x}</li>
            ))}
          </ol>
        </div>
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-700">Pytania</h4>
          <ul className="mt-1 list-disc space-y-0.5 pl-5">
            {conversation.questions.map((x) => (
              <li key={x}>{x}</li>
            ))}
          </ul>
        </div>
        <div>
          <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-700">Granice roli przełożonego</h4>
          <ul className="mt-1 list-disc space-y-0.5 pl-5">
            {conversation.role_boundaries.map((x) => (
              <li key={x}>{x}</li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  );
}

/** „Kultura doceniania” — komunikat SBI (sytuacja, zachowanie, wpływ) przed oceną; tekst do edycji przez przełożonego. */
function AppreciationCard({ view, employeeName }: { view: CycleView; employeeName: string }) {
  const { cycle, notes } = view;
  const [observations, setObservations] = useState(() => (notes ?? []).slice(-3).map((n) => n.content).join("\n"));
  const [tasks, setTasks] = useState("");
  const [result, setResult] = useState<AppreciationResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [copied, setCopied] = useState(false);
  const lines = (text: string) => text.split("\n").map((l) => l.trim()).filter(Boolean);

  const generate = async () => {
    setBusy(true);
    setError(null);
    try {
      setResult(
        await api<AppreciationResponse>(coreAi("appreciation-nudge", cycle.id), {
          body: { employee_name: employeeName, observations: lines(observations), completedTasks: lines(tasks) },
        }),
      );
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  const copy = async () => {
    if (!result) return;
    try {
      await navigator.clipboard.writeText(result.message);
      setCopied(true);
      setTimeout(() => setCopied(false), COPY_FEEDBACK_MS);
    } catch (e) {
      setError(e);
    }
  };

  return (
    <Card title="Kultura doceniania" actions={result && <ModeBadge mode={result.mode} />}>
      <p className="mb-3 text-sm text-slate-700">
        Docenienie przed rozmową oceniającą w modelu SBI: sytuacja, zachowanie, wpływ. Każda obserwacja w osobnym wierszu.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Obserwacje (fakty z notatek)">
          <textarea className={inputClass} rows={4} value={observations} onChange={(e) => setObservations(e.target.value)} />
        </Field>
        <Field label="Zrealizowane zadania">
          <textarea className={inputClass} rows={4} value={tasks} onChange={(e) => setTasks(e.target.value)} />
        </Field>
      </div>
      <div className="mt-3">
        <Button variant="ai" onClick={generate} busy={busy} disabled={!observations.trim() && !tasks.trim()}>
          AI: Przygotuj komunikat doceniający
        </Button>
      </div>
      <div className="mt-3">
        <ProblemAlert error={error} />
      </div>
      {result && (
        <div className="mt-4 space-y-3 text-sm">
          <dl className="grid gap-2 sm:grid-cols-3">
            {(
              [
                ["Sytuacja", result.situation],
                ["Zachowanie", result.behavior],
                ["Wpływ", result.impact],
              ] as const
            ).map(([label, value]) => (
              <div key={label} className="rounded border border-slate-200 p-2">
                <dt className="text-xs font-semibold uppercase tracking-wide text-slate-700">{label}</dt>
                <dd className="mt-0.5">{value}</dd>
              </div>
            ))}
          </dl>
          <div>
            <h3 className="text-sm font-semibold text-navy-900">Gotowy komunikat (do edycji)</h3>
            <p className="mt-1 whitespace-pre-wrap rounded bg-slate-50 p-2 ring-1 ring-inset ring-slate-200">{result.message}</p>
            <Button variant="secondary" className="mt-2" onClick={copy}>
              {copied ? "✓ Skopiowano" : "Kopiuj komunikat"}
            </Button>
          </div>
          <MaskedPrompt prompt={result.masked_prompt} />
          <AiComplianceBadge meta={result.complianceMeta} />
          <AiDisclaimer text={result.ai_disclaimer} />
        </div>
      )}
    </Card>
  );
}
