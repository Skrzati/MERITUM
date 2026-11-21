// Kontrakty zgodne z DTO backend-core (api/Dtos.java) i ai-gateway (app/main.py).

export type Role = "EVALUATOR" | "EVALUATED" | "HR" | "DG" | "KPRM_ADMIN";
export type SheetType = "A" | "B" | "C";
export type CycleStatus =
  | "DRAFT_CRITERIA"
  | "CRITERIA_CONFIRMED"
  | "OBSERVATION"
  | "EVALUATION_DRAFT"
  | "AWAITING_ACK"
  | "COMPLETED"
  | "APPEALED";
export type PerformanceLevel = "SIGNIFICANTLY_BELOW" | "BELOW" | "MEETS" | "ABOVE" | "SIGNIFICANTLY_ABOVE";
export type Outcome = "POSITIVE" | "NEGATIVE";
export type DgDecision = "CHANGE" | "REDO";
export type SignatureMethod = "MOBYWATEL_QES" | "PAPER_HYBRID" | "SYSTEM_DEMO";
export type RescheduleMode = "EVALUATOR_CHANGE" | "EMPLOYEE_ABSENCE" | "EVALUATOR_ABSENCE" | "EMPLOYEE_REQUEST";
export type RescheduleStatus = "PENDING" | "APPLIED" | "REJECTED";
export type ScopeChangeOutcome = "NONE" | "CRITERIA_RESET" | "EVALUATION_REQUIRED";
export type InterviewStage = "INITIAL" | "EVALUATION" | "FEEDBACK";
export type IprzStatus = "DRAFT" | "PROPOSED" | "APPROVED";
export type IprzFormType = "SZKOLENIE" | "MENTORING" | "SAMOKSZTALCENIE" | "PROJEKT" | "INNE";
export type IprzItemStatus = "PLANNED" | "IN_PROGRESS" | "DONE";
export type EscalationLevel = "OCENIAJACY" | "ZASTEPCA" | "KADRY" | "DYREKTOR_GENERALNY";

export interface User {
  id: number;
  officeId: number;
  fullName: string;
  /** Brak w /api/auth/demo-users (endpoint publiczny) — tylko w danych kadrowych. */
  email?: string;
  role: Role;
  civilServant: boolean;
  supervisorId: number | null;
  firstName?: string | null;
  lastName?: string | null;
  department?: string | null;
  positionTitle?: string | null;
  appointmentYear?: number | null;
  civilServiceGrade?: number | null;
  lastGradeDate?: string | null;
  /** Z /api/auth/demo-users: urząd użytkownika (organ rozpatrujący sprzeciw zależy od stanowiska DG). */
  officeName?: string | null;
  officeCity?: string | null;
  officeHasDgRole?: boolean;
  /** Z danych kadrowych: false = konto zanonimizowane. */
  active?: boolean;
}

export type HistorySourceType = "SYSTEM" | "HISTORICAL" | "MANUAL";

export interface PreviousEvaluation {
  outcome: Outcome;
  level: PerformanceLevel;
  evaluatedOn: string;
  source: HistorySourceType;
}

export interface HistoryEntry {
  id: number;
  cycleId: number | null;
  outcome: Outcome;
  level: PerformanceLevel;
  evaluatedOn: string;
  source: "SYSTEM" | "HISTORICAL";
}

export interface Substitution {
  id: number;
  officeId: number;
  evaluatorId: number;
  deputyId: number;
  startDate: string;
  endDate: string;
  reason: string;
  createdBy: number;
  activeToday: boolean;
}

export interface DuePreview {
  startDate: string;
  dueDate: string;
  criteriaDeadline: string;
  periodMonths: number;
  schemaVersion: string;
  suggestedSheet: SheetType;
  defaultEvaluatorId: number | null;
  defaultPlace: string | null;
}

export interface BulkCreateResult {
  created: Cycle[];
  skipped: { employeeId: number; reason: string }[];
}

export type RemovalOutcome = "DELETED" | "ANONYMIZED";

export interface LoginResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  identityProvider: string;
  user: User;
}

export interface Criterion {
  code: string;
  name: string;
  mandatory: boolean;
}

export interface GradeRequest {
  requested: boolean;
  motivation: string;
}

export interface Cycle {
  id: number;
  officeId: number;
  employeeId: number;
  evaluatorId: number;
  schemaVersionId: number;
  sheetType: SheetType;
  status: CycleStatus;
  reevaluation: boolean;
  startDate: string;
  dueDate: string;
  criteria: Criterion[] | null;
  scores: Record<string, number> | null;
  averageScore: number | null;
  performanceLevel: PerformanceLevel | null;
  finalResult: Outcome | null;
  resultExplanation: string | null;
  justification: string | null;
  iprzNotes: string | null;
  gradeRequest: GradeRequest | null;
  acknowledgedAt: string | null;
  refusalReason: string | null;
  refusalDate: string | null;
  appealDeadline: string | null;
  appealText: string | null;
  appealedAt: string | null;
  dgDecision: DgDecision | null;
  dgComment: string | null;
  dgDecidedAt: string | null;
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
  /** false — mały urząd bez stanowiska DG: sprzeciw rozpatruje kierownik urzędu. */
  officeHasDgRole: boolean;
  appealAuthority: string;
  previousEvaluation: PreviousEvaluation | null;
  iprzStatus: IprzStatus;
  iprzApprovedBy: number | null;
  iprzApprovedAt: string | null;
  iprzEmployeeAckAt: string | null;
}

export interface SelfPrep {
  cycleId: number;
  achievements: string | null;
  challenges: string | null;
  developmentNeeds: string | null;
  sharedWithEvaluator: boolean;
  updatedAt: string | null;
}

export interface FeedbackCheckin {
  id: number;
  cycleId: number;
  authorId: number;
  conductedOn: string;
  topic: string;
  summary: string;
  nextSteps: string | null;
  sharedWithEmployee: boolean;
}

export interface IprzItem {
  id: number;
  cycleId: number;
  criterionCode: string | null;
  actionTitle: string;
  formType: IprzFormType;
  targetDate: string | null;
  status: IprzItemStatus;
}

export interface TeamOverview {
  competencies: { employeeId: number; fullName: string; positionTitle: string | null; cycleId: number | null; scores: Record<string, number> }[];
  teamAverages: { code: string; name: string; average: number; rated: number }[];
  mentoring: {
    criterionCode: string;
    criterionName: string;
    mentorId: number;
    mentorName: string;
    mentorScore: number;
    menteeId: number;
    menteeName: string;
    menteeScore: number;
  }[];
  signals: {
    employeeId: number;
    fullName: string;
    activeCycleId: number | null;
    absenceDays6m: number;
    scopeChangesCount: number;
    overloadNotesCount: number;
    daysSinceLastActivity: number | null;
    appreciationSnippet: string | null;
  }[];
}

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

export interface NotificationItem {
  type:
    | "OVERDUE"
    | "DUE_SOON"
    | "CRITERIA_DUE"
    | "APPEAL_DECISION_DUE"
    | "MANDATORY_GRADE_DUE"
    | "QUARTERLY_CHECKIN"
    | "GRADE_REQUEST_SUBMITTED"
    | "RESCHEDULE_PENDING"
    | "APPEAL_DECIDED";
  severity: "HIGH" | "MEDIUM" | "LOW";
  cycleId: number;
  dueDate: string | null;
  message: string;
}

export interface OverdueItem {
  cycleId: number;
  employeeId: number;
  employeeName: string | null;
  evaluatorId: number;
  evaluatorName: string | null;
  dueDate: string;
  overdueDays: number;
  delayReason: string | null;
  escalationLevel: EscalationLevel;
}

export interface AuditLogItem {
  id: number;
  officeId: number | null;
  actorId: number | null;
  action: string;
  entityType: string;
  entityId: number | null;
  payload: Record<string, unknown> | null;
  createdAt: string;
}

export interface Note {
  id: number;
  cycleId: number;
  authorId: number;
  content: string;
  isSharedAfterEval: boolean;
  createdAt: string;
}

export interface EvaluationResult {
  average: number;
  averageDisplay: string;
  level: PerformanceLevel;
  outcome: Outcome;
  explanation: string;
  violations: string[];
}

export interface EvaluationResponse {
  cycle: Cycle;
  result: EvaluationResult | null;
}

export interface CatalogCriterion {
  code: string;
  name: string;
  /** Opisy zachowań z załącznika nr 1. */
  description?: string[];
}

export interface SchemaConfig {
  legalBasis?: string;
  sheetTypes?: Partial<Record<SheetType, string>>;
  criteriaRules?: { maxAdditional?: number; allowCustom?: boolean };
  criteria: CatalogCriterion[];
  mandatoryBySheet?: Partial<Record<SheetType, string[]>>;
  [key: string]: unknown;
}

export interface Schema {
  id: number;
  versionCode: string;
  effectiveFrom: string;
  periodMonths: number;
  config: SchemaConfig;
  createdBy: number | null;
}

/** Liczność po k-anonimizacji na serwerze: 1..k-1 → {value: null, suppressed: true}. */
export interface AnonymizedCount {
  value: number | null;
  suppressed: boolean;
}

export interface WellbeingSummary {
  responses: AnonymizedCount;
  /** Średnie 1–5 — null, gdy odpowiedzi jest mniej niż k. */
  avgWorkload: number | null;
  avgEnergy: number | null;
  avgSupport: number | null;
  wantsConversation: AnonymizedCount;
}

export interface StatsSummary {
  kAnonymity: number;
  totalCycles: AnonymizedCount;
  byStatus: Record<string, AnonymizedCount>;
  byOutcome: Record<string, AnonymizedCount>;
  byLevel: Record<string, AnonymizedCount>;
  overdue: AnonymizedCount;
  dueIn7Days: AnonymizedCount;
  dueIn30Days: AnonymizedCount;
  appeals: AnonymizedCount;
  wellbeing: WellbeingSummary;
}

export interface HandoverResult {
  employeeId: number;
  previousEvaluatorId: number | null;
  newEvaluatorId: number;
  cycleIds: number[];
  notesTransferred: number;
}

export interface ImportResult {
  imported: number;
  skipped: string[];
  errors: string[];
}

export type AiMode = "LIVE" | "MOCK" | "MOCK_FALLBACK";

export interface SocraticResponse {
  masked_prompt: string;
  socratic_questions: string[];
  draft_justification: string;
  ai_disclaimer: string;
  mode: AiMode;
}

export interface ConsistencyWarning {
  code: string;
  severity: "ERROR" | "WARNING";
  message: string;
}

export interface ConsistencyResponse {
  warnings: ConsistencyWarning[];
  auto_modified: boolean;
}

export interface HrPartnerResponse {
  masked_prompt: string;
  burnout_risk_signal: boolean;
  conversation_starters: string[];
  organizational_suggestions: string[];
  appreciation_moment: string;
  ai_disclaimer: string;
  mode: AiMode;
}

export interface Problem {
  title?: string;
  status?: number;
  detail?: string;
  violations?: string[];
  result?: EvaluationResult;
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
