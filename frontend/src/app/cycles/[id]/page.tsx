"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { AckTab } from "@/components/cycle/AckTab";
import { ChangesTab } from "@/components/cycle/ChangesTab";
import type { CycleDetails, UserDetails } from "@/components/cycle/contracts";
import type { CycleView } from "@/components/cycle/context";
import { CriteriaTab } from "@/components/cycle/CriteriaTab";
import { EvaluationTab } from "@/components/cycle/EvaluationTab";
import { InterviewTab } from "@/components/cycle/InterviewTab";
import { NotesTab } from "@/components/cycle/NotesTab";
import { ProgressSteps } from "@/components/cycle/ProgressSteps";
import { Badge, Card, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api, ApiError } from "@/lib/api";
import { formatDate, LEVEL_LABEL, OUTCOME_LABEL, STATUS_LABEL, STATUS_TONE } from "@/lib/labels";
import type { Note, Schema } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const TABS = [
  { id: "criteria", label: "1. Kryteria" },
  { id: "interview", label: "2. Rozmowy i IPRZ" },
  { id: "notes", label: "3. Notatki i HR Partner" },
  { id: "evaluation", label: "4. Ocena i Sokratejskie AI" },
  { id: "changes", label: "5. Termin (§ 8) i Zakres (§ 9)" },
  { id: "ack", label: "6. Zapoznanie, Podpis i PDF" },
] as const;
type TabId = (typeof TABS)[number]["id"];

interface Loaded {
  cycle: CycleDetails;
  schema: CycleView["schema"];
  notes: Note[] | null;
  notesError: unknown;
}

export default function CyclePage() {
  const { id } = useParams<{ id: string }>();
  const { current, userName, userById } = useUser();
  const [tab, setTab] = useState<TabId>("criteria");

  const state = useAsync<Loaded | null>(async () => {
    if (!current) return null;
    const cycle = await api<CycleDetails>(`/api/cycles/${id}`);
    const [schema, notesResult] = await Promise.all([
      api<Schema>(`/api/schemas/${cycle.schemaVersionId}`).then((s) => s as CycleView["schema"], () => null),
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
    employee: userById(cycle.employeeId) as UserDetails | undefined,
    isEvaluator: current.id === cycle.evaluatorId,
    isEmployee: current.id === cycle.employeeId,
    isOfficeHr: current.role === "HR" && current.officeId === cycle.officeId,
    isOfficeDg: current.role === "DG" && current.officeId === cycle.officeId,
    refresh: state.reload,
  };
  const myRole = view.isEvaluator ? "oceniający" : view.isEmployee ? "oceniany" : view.isOfficeDg ? "DG" : view.isOfficeHr ? "kadry" : "podgląd";

  return (
    <div className="space-y-6">
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
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={STATUS_TONE[cycle.status]}>{STATUS_LABEL[cycle.status]}</Badge>
            {cycle.overdueDays > 0 && <Badge tone="red">po terminie {cycle.overdueDays} dni</Badge>}
            {cycle.mandatoryGradeEligible && <Badge tone="green">Obligatoryjny stopień (art. 89 ust. 2)</Badge>}
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
        {tab === "evaluation" && <EvaluationTab view={view} onOpenInterview={() => setTab("interview")} />}
        {tab === "changes" && <ChangesTab view={view} />}
        {tab === "ack" && <AckTab view={view} />}
      </div>
    </div>
  );
}
