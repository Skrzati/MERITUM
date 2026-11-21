// Rozszerzenia kontraktów z Etapów 5–6B (Dtos.java, ai-gateway/app/main.py) — do przeniesienia do lib/types.ts.
import type { AiMode, Cycle, SchemaConfig, User } from "@/lib/types";

export type SignatureMethod = "MOBYWATEL_QES" | "PAPER_HYBRID" | "SYSTEM_DEMO";
export type RescheduleMode = "EVALUATOR_CHANGE" | "EMPLOYEE_ABSENCE" | "EVALUATOR_ABSENCE" | "EMPLOYEE_REQUEST";
export type RescheduleStatus = "PENDING" | "APPLIED" | "REJECTED";
export type ScopeChangeOutcome = "NONE" | "CRITERIA_RESET" | "EVALUATION_REQUIRED";
export type InterviewStage = "INITIAL" | "EVALUATION";

export interface CycleDetails extends Cycle {
  criteriaDeadline: string | null;
  criteriaInterviewDate: string | null;
  criteriaConfirmedOn: string | null;
  evaluationInterviewDate: string | null;
  evaluatedOn: string | null;
  place: string | null;
  appealDecisionDeadline: string | null;
  mandatoryGradeEligible: boolean;
  mandatoryGradeDeadline: string | null;
  delayReason: string | null;
  signatureMethod: SignatureMethod | null;
  overdueDays: number;
}

export interface UserDetails extends User {
  firstName?: string | null;
  lastName?: string | null;
  department?: string | null;
  positionTitle?: string | null;
  civilServiceGrade?: number | null;
}

export interface CatalogCriterion {
  code: string;
  name: string;
  description?: string[];
}

export type SchemaConfigDetails = SchemaConfig & { criteria: CatalogCriterion[] };

export interface RescheduleRequest {
  id: number;
  cycleId: number;
  mode: RescheduleMode;
  statutePoint: number;
  status: RescheduleStatus;
  reason: string;
  previousDueDate: string;
  proposedDueDate: string | null;
  newDueDate: string | null;
  noticeText: string | null;
  requestedBy: number;
  decidedBy: number | null;
  decisionComment: string | null;
  createdAt: string;
  decidedAt: string | null;
}

export interface ScopeChange {
  id: number;
  cycleId: number;
  changeDate: string;
  newPositionTitle: string | null;
  description: string;
  significant: boolean;
  outcome: ScopeChangeOutcome;
  actionDeadline: string | null;
  recordedBy: number;
}

export interface HrFacts {
  absenceDays: number;
  scopeChangesCount: number;
  overloadNotesCount: number;
  totalNotesCount: number;
  daysSinceLastNote: number | null;
  overdueDays: number;
}

export interface CriterionSuggestion {
  code: string;
  name: string;
  reason: string;
  expectations: string[];
}

export interface SuggestCriteriaResponse {
  masked_prompt: string;
  suggestions: CriterionSuggestion[];
  ai_disclaimer: string;
  mode: AiMode;
}

export interface InterviewPrepResponse {
  masked_prompt: string;
  stage: InterviewStage;
  goal: string;
  agenda: { topic: string; legal_basis: string; questions: string[] }[];
  difficult_situations: { situation: string; tip: string }[];
  development_catalog: { criterion: string; actions: string[] }[];
  ai_disclaimer: string;
  mode: AiMode;
}

/** Najwyższy stopień służbowy w służbie cywilnej — wniosek o kolejny stopień jest bezprzedmiotowy. */
export const MAX_CIVIL_SERVICE_GRADE = 9;

/** Wywołanie AI przez backend-core (audyt AI Act art. 26). */
export const coreAi = (operation: string, cycleId: number) => `/api/core-ai/${operation}?cycleId=${cycleId}`;

/** Status terminu oceny: w terminie / zbliża się (≤ 30 dni) / po terminie. */
export const DUE_SOON_DAYS = 30;
export function dueState(c: Pick<CycleDetails, "dueDate" | "overdueDays" | "status" | "evaluatedOn">, todayIso: string) {
  if (c.overdueDays > 0) return { tone: "red" as const, label: `po terminie ${c.overdueDays} dni` };
  if (c.status === "COMPLETED" || c.status === "APPEALED" || c.evaluatedOn) return { tone: "green" as const, label: "ocena sporządzona" };
  const days = Math.round((Date.parse(c.dueDate) - Date.parse(todayIso)) / 86_400_000);
  return days <= DUE_SOON_DAYS
    ? { tone: "amber" as const, label: `zbliża się (${days} dni)` }
    : { tone: "green" as const, label: "w terminie" };
}
