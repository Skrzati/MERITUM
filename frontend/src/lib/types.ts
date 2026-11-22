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
  | "APPEALED"
  | "APPEALED_TO_COURT";
export type PerformanceLevel = "SIGNIFICANTLY_BELOW" | "BELOW" | "MEETS" | "ABOVE" | "SIGNIFICANTLY_ABOVE";
export type Outcome = "POSITIVE" | "NEGATIVE";
export type DgDecision = "CHANGE" | "REDO";
/** Trzy tryby podpisu; MOBYWATEL_QES / PAPER_HYBRID / SYSTEM_DEMO — dawne wartości (backend mapuje je przez mode()). */
export type SignatureMode = "QUALIFIED_OR_TRUSTED" | "INTERNAL_AUDIT_STAMP" | "HYBRID_PAPER_SCAN";
export type SignatureMethod = SignatureMode | "MOBYWATEL_QES" | "PAPER_HYBRID" | "SYSTEM_DEMO";
export type DifficultScenario = "SCORE_DROP" | "NEGATIVE_EVALUATION" | "OVERLOAD_SIGNALS";
export type RescheduleMode = "EVALUATOR_CHANGE" | "EMPLOYEE_ABSENCE" | "EVALUATOR_ABSENCE" | "EMPLOYEE_REQUEST";
export type RescheduleStatus = "PENDING" | "APPLIED" | "REJECTED";
export type ScopeChangeOutcome = "NONE" | "CRITERIA_RESET" | "EVALUATION_REQUIRED";
export type InterviewStage = "INITIAL" | "EVALUATION" | "FEEDBACK";
export type IprzStatus = "DRAFT" | "PROPOSED" | "APPROVED";
export type IprzFormType = "SZKOLENIE" | "MENTORING" | "SAMOKSZTALCENIE" | "PROJEKT" | "INNE";
export type IprzItemStatus = "PLANNED" | "IN_PROGRESS" | "DONE";
/** Status zatrudnienia w SC — PRACOWNIK_SC_OKRESLONY i WYZSZE_STANOWISKO_SC nie podlegają ocenie okresowej. */
export type EmploymentStatus = "URZEDNIK_SC" | "PRACOWNIK_SC_NIEOKRESLONY" | "PRACOWNIK_SC_OKRESLONY" | "WYZSZE_STANOWISKO_SC";
export type EscalationLevel = "OCENIAJACY" | "ZASTEPCA" | "KADRY" | "DYREKTOR_GENERALNY";

export interface User {
  id: number;
  officeId: number;
  fullName: string;
  /** Brak w /api/auth/demo-users (endpoint publiczny) — tylko w danych kadrowych. */
  email?: string;
  role: Role;
  /** Pochodna statusu: true wyłącznie dla URZEDNIK_SC (stopnie służbowe, art. 89). */
  civilServant: boolean;
  employmentStatus: EmploymentStatus;
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
  /** Opis stanowiska pracy (art. 84) — backend przekazuje go do podpowiedzi kryteriów AI. */
  jobDescription?: string | null;
  /** Data nawiązania stosunku pracy — retencja akt: po 31.12.2018 10 lat, wcześniej / brak daty 50 lat. */
  employmentStartDate?: string | null;
  /** Role łączone w małym urzędzie (np. DG + EVALUATOR + HR); KPRM_ADMIN nie może być rolą dodatkową. */
  secondaryRoles?: Role[];
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
  employmentStatus: EmploymentStatus;
  /** ≠ null — pracownik nie podlega ocenie okresowej (art. 37–38 / wyższe stanowisko SC). */
  blockedReason: string | null;
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
  /** Art. 83 ust. 5 — spór sądowy: arkusz nie podlega czyszczeniu ani brakowaniu. */
  legalHold: boolean;
  courtAppealFiledOn: string | null;
  courtCaseReference: string | null;
  /** Liczba rozstrzygnięć sprzeciwu (po REDO przysługuje ponowny sprzeciw — art. 83 ust. 4). */
  appealRound: number;
  /** false po zmianie oceny przez organ — pozostaje odwołanie do sądu pracy. */
  appealAllowed: boolean;
  /** Dane pomocnicze (notatki robocze, ankiety) usunięte po zamknięciu cyklu — arkusz pozostaje w części B akt. */
  workingNotesPurgedAt: string | null;
  /** Kod kryterium → ustalone oczekiwania na poziomie 5 pkt (§ 5 ust. 1 pkt 3). */
  criteriaExpectations: Record<string, string> | null;
  /** SHA-256 treści arkusza w chwili zapoznania. */
  signatureHash: string | null;
  /** Kod weryfikacyjny drukowany na PDF. */
  signatureVerificationCode: string | null;
  signedAt: string | null;
  paperSignedOn: string | null;
  scanReference: string | null;
}

/** Prywatny dziennik pracownika — widoczny wyłącznie dla właściciela, poza aktami i EZD. */
export interface EmployeePrivateNote {
  id: number;
  cycleId: number | null;
  content: string;
  createdAt: string;
  updatedAt: string | null;
}

export interface IprzSummary {
  approvedPlans: number;
  byCriterion: { criterionCode: string; criterionName: string; actions: number; employees: number }[];
  byFormType: Partial<Record<IprzFormType, number>>;
  topActions: { actionTitle: string; count: number }[];
}

export interface DepartmentCalibration {
  department: string;
  evaluations: number;
  averageScore: number | null;
  levels: Partial<Record<PerformanceLevel, AnonymizedCount>>;
  positive: AnonymizedCount;
  negative: AnonymizedCount;
}

export interface Calibration {
  kAnonymity: number;
  year: number | null;
  departments: DepartmentCalibration[];
  suppressedDepartments: number;
  officeAverage: number | null;
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
    | "APPEAL_DECIDED"
    | "HR_CRITICAL_ALERT"
    | "HR_INFO_ALERT"
    | "PRE_INTERVIEW_SURVEY_SENT"
    | "PRE_INTERVIEW_SURVEY_OPEN";
  severity: "HIGH" | "MEDIUM" | "LOW";
  cycleId: number | null;
  dueDate: string | null;
  message: string;
}

/** Konto w /admin (tryb Mały Urząd) — backend nie zwraca skrótu hasła. */
export interface Account {
  user: User;
  hasLocalPassword: boolean;
  passwordChangedAt: string | null;
}

export interface Office {
  id: number;
  name: string;
  city: string | null;
  hasDgRole: boolean;
  localAuthEnabled: boolean;
}

/** Ankieta przygotowawcza przed rozmową (PreInterviewSurveyService.SurveyView) — niezależna od ankiety dobrostanu. */
export interface SurveyQuestion {
  id: string;
  text: string;
  custom: boolean;
  addedBy: number | null;
  answer: string | null;
}

export interface PreInterviewSurvey {
  cycleId: number;
  status: "DRAFT" | "SENT";
  sentAt: string | null;
  allowCustom: boolean;
  /** false — przełożony przed wysłaniem albo kadry: odpowiedzi ukryte. */
  answersVisible: boolean;
  /** false — przełożony nie udostępnił jeszcze ankiety (oceniany dostaje pustą listę pytań). */
  open: boolean;
  /** Od tego dnia oceniany widzi ankietę; null — nieudostępniona. */
  opensOn: string | null;
  answerDeadline: string | null;
  /** Wstęp od przełożonego wyświetlany ocenianemu nad pytaniami. */
  introMessage: string | null;
  /** true — bieżący użytkownik (przełożony / zastępca) może ustalić termin udostępnienia. */
  canSchedule: boolean;
  questions: SurveyQuestion[];
}

export interface PurgeResult {
  cyclesPurged: number;
  notesDeleted: number;
  surveysDeleted: number;
  skippedLegalHold: number;
}

export interface HrAlert {
  employeeId: number;
  employeeName: string;
  employmentStatus: EmploymentStatus;
  cycleId: number | null;
  type: "TWO_NEGATIVE_TERMINATION" | "TWO_NEGATIVE_INFO" | "MANDATORY_GRADE";
  severity: "CRITICAL" | "INFO";
  legalBasis: string;
  message: string;
  since: string;
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

export type AiMode = "LIVE" | "MOCK" | "MOCK_FALLBACK" | "RULES";

/** complianceMeta z każdej odpowiedzi /api/ai/* (AI Act, zał. III pkt 4). */
export interface ComplianceMeta {
  piiAnonymized: boolean;
  redactedCount: number;
  humanInTheLoopRequired: boolean;
  advisoryOnly: boolean;
  disclaimer: string;
}

export interface SocraticResponse {
  masked_prompt: string;
  socratic_questions: string[];
  draft_justification: string;
  ai_disclaimer: string;
  mode: AiMode;
  complianceMeta?: ComplianceMeta;
}

export interface ConsistencyWarning {
  code: string;
  severity: "ERROR" | "WARNING";
  message: string;
}

export interface ConsistencyResponse {
  warnings: ConsistencyWarning[];
  auto_modified: boolean;
  complianceMeta?: ComplianceMeta;
}

export interface HrPartnerResponse {
  masked_prompt: string;
  burnout_risk_signal: boolean;
  conversation_starters: string[];
  organizational_suggestions: string[];
  appreciation_moment: string;
  ai_disclaimer: string;
  mode: AiMode;
  complianceMeta?: ComplianceMeta;
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
  complianceMeta?: ComplianceMeta;
}

export interface InterviewPrepResponse {
  masked_prompt: string;
  stage: InterviewStage;
  goal: string;
  agenda: { topic: string; legal_basis: string; questions: string[] }[];
  difficult_situations: { situation: string; tip: string }[];
  development_catalog: DevelopmentEntry[];
  difficult_conversation: DifficultConversation | null;
  ai_disclaimer: string;
  mode: AiMode;
  complianceMeta?: ComplianceMeta;
}

/** Działania rozwojowe w modelu 70-20-10; wewnętrzne działania mają koszt 0 zł. */
export interface DevelopmentEntry {
  criterion: string;
  actions: string[];
  estimatedCostPln: number;
  onTheJob70: string[];
  social20: string[];
  formal10: string[];
}

/** Stała karta trudnej rozmowy (bez LLM): struktura, pytania i granice roli przełożonego. */
export interface DifficultConversation {
  scenario: DifficultScenario;
  title: string;
  structure: string[];
  questions: string[];
  role_boundaries: string[];
}

export interface AppreciationResponse {
  masked_prompt: string;
  situation: string;
  behavior: string;
  impact: string;
  message: string;
  ai_disclaimer: string;
  mode: AiMode;
  complianceMeta?: ComplianceMeta;
}

export interface TeamMentoringResponse {
  masked_prompt: string;
  pairs: { mentor: string; mentee: string; criterion: string; rationale: string }[];
  unmatched: { mentee: string; criterion: string }[];
  ai_disclaimer: string;
  mode: AiMode;
  complianceMeta?: ComplianceMeta;
}
