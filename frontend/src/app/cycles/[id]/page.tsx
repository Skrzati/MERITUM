"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { AckTab } from "@/components/cycle/AckTab";
import { ChangesTab } from "@/components/cycle/ChangesTab";
import type { CycleView } from "@/components/cycle/context";
import { CriteriaTab } from "@/components/cycle/CriteriaTab";
import { EvaluationTab } from "@/components/cycle/EvaluationTab";
import { FormCorrectionPanel } from "@/components/cycle/FormCorrectionPanel";
import { InterviewTab } from "@/components/cycle/InterviewTab";
import { IprzPanel } from "@/components/cycle/IprzPanel";
import { NotesTab } from "@/components/cycle/NotesTab";
import { ProgressSteps } from "@/components/cycle/ProgressSteps";
import { Badge, Button, Card, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api, ApiError } from "@/lib/api";
import { formatDate, LEVEL_LABEL, OUTCOME_LABEL, STATUS_LABEL, STATUS_TONE, type Tone } from "@/lib/labels";
import type { Cycle, CycleStatus, EmploymentStatus, Note, Schema } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const TABS = [
  { id: "criteria", label: "1. Kryteria" },
  { id: "notes", label: "2. Notatki" },
  { id: "interview", label: "3. Rozmowa i ankieta" },
  { id: "evaluation", label: "4. Ocena i IPRZ" },
  { id: "ack", label: "5. Zapoznanie i PDF" },
  { id: "changes", label: "6. Zmiany i korekty" },
] as const;
type TabId = (typeof TABS)[number]["id"];

const EMPLOYMENT_BADGE: Record<EmploymentStatus, { tone: Tone; label: string }> = {
  URZEDNIK_SC: { tone: "blue", label: "Urzędnik służby cywilnej (mianowany · przysługuje wniosek o stopień — art. 89)" },
  PRACOWNIK_SC_NIEOKRESLONY: { tone: "slate", label: "Pracownik służby cywilnej (umowa na czas nieokreślony)" },
  PRACOWNIK_SC_OKRESLONY: { tone: "amber", label: "Pracownik służby cywilnej (umowa na czas określony — pierwsza ocena, art. 37–38)" },
  WYZSZE_STANOWISKO_SC: { tone: "amber", label: "Wyższe stanowisko w służbie cywilnej (bez oceny okresowej)" },
};

interface NextStep {
  text: string;
  tab: TabId;
  action: string;
}

type StepMap = Partial<Record<CycleStatus, NextStep>>;

const EVALUATOR_STEPS: StepMap = {
  DRAFT_CRITERIA: { text: "Wybierz kryteria oceny i omów je z ocenianym na rozmowie rozpoczynającej.", tab: "criteria", action: "Przejdź do kryteriów" },
  CRITERIA_CONFIRMED: { text: "Kryteria są potwierdzone — zapisuj na bieżąco obserwacje w notatkach.", tab: "notes", action: "Dodaj notatkę" },
  OBSERVATION: { text: "Zapisuj obserwacje w notatkach, a przed terminem przygotuj rozmowę oceniającą.", tab: "notes", action: "Dodaj notatkę" },
  EVALUATION_DRAFT: { text: "Dokończ arkusz oceny i zaplanuj indywidualny program rozwoju (IPRZ).", tab: "evaluation", action: "Wypełnij ocenę" },
  AWAITING_ACK: { text: "Oceniany powinien zapoznać się z oceną — w razie odmowy odnotuj ją.", tab: "ack", action: "Przejdź do zapoznania" },
  COMPLETED: { text: "Ocena jest zakończona — możesz pobrać arkusz w PDF.", tab: "ack", action: "Pobierz arkusz" },
  APPEALED: { text: "Oceniany wniósł sprzeciw — poczekaj na decyzję organu.", tab: "ack", action: "Zobacz sprzeciw" },
};

const EMPLOYEE_STEPS: StepMap = {
  DRAFT_CRITERIA: { text: "Sprawdź kryteria ustalone przez przełożonego i potwierdź je.", tab: "criteria", action: "Sprawdź kryteria" },
  CRITERIA_CONFIRMED: { text: "Przygotuj się do rozmowy — odpowiedz na ankietę i zapisz swoje osiągnięcia.", tab: "interview", action: "Przygotuj się do rozmowy" },
  OBSERVATION: { text: "Przygotuj się do rozmowy — odpowiedz na ankietę i zapisz swoje osiągnięcia.", tab: "interview", action: "Przygotuj się do rozmowy" },
  EVALUATION_DRAFT: { text: "Przełożony przygotowuje ocenę — możesz przygotować się do rozmowy oceniającej.", tab: "interview", action: "Przygotuj się do rozmowy" },
  AWAITING_ACK: { text: "Zapoznaj się z oceną i potwierdź to podpisem.", tab: "ack", action: "Zapoznaj się z oceną" },
  COMPLETED: { text: "Ocena jest zakończona — od zapoznania masz 7 dni na wniesienie sprzeciwu.", tab: "ack", action: "Zobacz ocenę i PDF" },
  APPEALED: { text: "Twój sprzeciw czeka na rozpatrzenie.", tab: "ack", action: "Zobacz sprzeciw" },
};

const COURT_STEP: NextStep = { text: "Sprawa jest w sądzie pracy — arkusz jest chroniony przed usunięciem.", tab: "ack", action: "Zobacz szczegóły" };

function nextStep(cycle: Cycle, view: CycleView): NextStep {
  if (cycle.status === "APPEALED_TO_COURT") return COURT_STEP;
  const byRole = view.isEvaluator ? EVALUATOR_STEPS[cycle.status] : view.isEmployee ? EMPLOYEE_STEPS[cycle.status] : undefined;
  if (byRole) return byRole;
  if (view.isOfficeDg && cycle.status === "APPEALED")
    return { text: "Rozpatrz sprzeciw ocenianego w ciągu 14 dni.", tab: "ack", action: "Rozpatrz sprzeciw" };
  if (view.isOfficeHr && cycle.overdueDays > 0)
    return { text: "Ocena jest po terminie — rozważ zmianę terminu lub oceniającego.", tab: "changes", action: "Przejdź do zmian" };
  return { text: `Bieżący etap: ${STATUS_LABEL[cycle.status]}. Możesz przejrzeć arkusz i dokumenty.`, tab: "ack", action: "Zobacz arkusz i PDF" };
}

interface Loaded {
  cycle: Cycle;
  schema: Schema | null;
  notes: Note[] | null;
  notesError: unknown;
}

export default function CyclePage() {
  const { id } = useParams<{ id: string }>();
  const { current, userName, userById } = useUser();
  const [tab, setTab] = useState<TabId>("criteria");

  const state = useAsync<Loaded | null>(async () => {
    if (!current) return null;
    const cycle = await api<Cycle>(`/api/cycles/${id}`);
    const [schema, notesResult] = await Promise.all([
      api<Schema>(`/api/schemas/${cycle.schemaVersionId}`).catch(() => null),
      api<Note[]>(`/api/cycles/${id}/notes`).then(
        (notes) => ({ notes, error: null as unknown }),
        (error: unknown) => ({ notes: null, error }),
      ),
    ]);
    return { cycle, schema, notes: notesResult.notes, notesError: notesResult.error };
  }, [id, current?.id]);

  if (!current || (state.loading && !state.data)) return <Spinner />;
  if (state.error) {
    return (
      <div className="space-y-4">
        <ProblemAlert error={state.error} />
        {state.error instanceof ApiError && state.error.status === 403 && (
          <p className="text-sm">Ten cykl nie jest dostępny dla aktywnego użytkownika — przełącz rolę w nagłówku.</p>
        )}
        <Link href="/" className="text-sm font-medium text-navy-700 underline">
          ← Wróć do pulpitu
        </Link>
      </div>
    );
  }
  if (!state.data) return null;

  const { cycle } = state.data;
  const view: CycleView = {
    ...state.data,
    me: current,
    employee: userById(cycle.employeeId),
    isEvaluator: current.id === cycle.evaluatorId,
    isEmployee: current.id === cycle.employeeId,
    isOfficeHr: current.role === "HR" && current.officeId === cycle.officeId,
    isOfficeDg: current.role === "DG" && current.officeId === cycle.officeId,
    refresh: state.reload,
  };
  const employment = view.employee?.employmentStatus ? EMPLOYMENT_BADGE[view.employee.employmentStatus] : null;
  const step = nextStep(cycle, view);
  const myRole = view.isEvaluator ? "oceniający" : view.isEmployee ? "oceniany" : view.isOfficeDg ? "DG" : view.isOfficeHr ? "kadry" : "podgląd";

  return (
    <div className="space-y-8">
      <Link href="/" className="text-sm font-medium text-navy-700 underline">
        ← Pulpit
      </Link>
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-navy-900">
              Ocena okresowa: {userName(cycle.employeeId)}
            </h1>
            <p className="mt-1 text-sm text-slate-700">
              Arkusz {cycle.sheetType}
              {state.data.schema?.config.sheetTypes?.[cycle.sheetType] ? ` — ${state.data.schema.config.sheetTypes[cycle.sheetType]}` : ""}
              {" · "}oceniający: {userName(cycle.evaluatorId)} · okres {formatDate(cycle.startDate)} – {formatDate(cycle.dueDate)}
            </p>
            {employment && (
              <p className="mt-3">
                <Badge tone={employment.tone}>{employment.label}</Badge>
              </p>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={STATUS_TONE[cycle.status]}>{STATUS_LABEL[cycle.status]}</Badge>
            {cycle.overdueDays > 0 && <Badge tone="red">po terminie {cycle.overdueDays} dni</Badge>}
            {cycle.mandatoryGradeEligible && <Badge tone="green">Obligatoryjny stopień (art. 89 ust. 2)</Badge>}
            {!cycle.officeHasDgRole && <Badge tone="amber">Urząd bez DG — sprzeciw: kierownik urzędu</Badge>}
            <Badge>schemat {state.data.schema?.versionCode ?? `#${cycle.schemaVersionId}`}</Badge>
            <Badge tone="blue">Twoja rola: {myRole}</Badge>
            {cycle.finalResult && (
              <Badge tone={cycle.finalResult === "POSITIVE" ? "green" : "red"}>
                {OUTCOME_LABEL[cycle.finalResult]}
                {cycle.performanceLevel ? ` · ${LEVEL_LABEL[cycle.performanceLevel]}` : ""}
              </Badge>
            )}
          </div>
        </div>
        <div className="mt-5">
          <ProgressSteps status={cycle.status} />
        </div>
      </Card>

      <section aria-labelledby="next-step-title" className="rounded border-l-4 border-gov-blue bg-gov-light p-5">
        <h2 id="next-step-title" className="text-xl font-bold text-gov-text">
          Co teraz zrobić?
        </h2>
        <p className="mt-2 text-base text-gov-text">{step.text}</p>
        <Button className="mt-4" onClick={() => setTab(step.tab)}>
          {step.action}
        </Button>
      </section>

      <div role="tablist" aria-label="Sekcje cyklu" className="flex flex-wrap gap-1 border-b border-slate-200">
        {TABS.map((t) => (
          <button
            key={t.id}
            role="tab"
            id={`tab-${t.id}`}
            aria-selected={tab === t.id}
            aria-controls={`panel-${t.id}`}
            onClick={() => setTab(t.id)}
            className={`-mb-px rounded-t-md border px-4 py-2 text-sm font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-600 ${
              tab === t.id ? "border-slate-200 border-b-white bg-white text-navy-900" : "border-transparent text-slate-700 hover:text-navy-900"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
        {tab === "criteria" && <CriteriaTab view={view} />}
        {tab === "interview" && <InterviewTab view={view} />}
        {tab === "notes" && <NotesTab view={view} />}
        {tab === "evaluation" && (
          <div className="space-y-6">
            <EvaluationTab view={view} onOpenInterview={() => setTab("interview")} />
            <IprzPanel view={view} />
          </div>
        )}
        {tab === "changes" && (
          <div className="space-y-6">
            <ChangesTab view={view} />
            <FormCorrectionPanel view={view} />
          </div>
        )}
        {tab === "ack" && <AckTab view={view} />}
      </div>
    </div>
  );
}
