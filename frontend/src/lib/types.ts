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

export interface User {
  id: number;
  officeId: number;
  fullName: string;
  email: string;
  role: Role;
  civilServant: boolean;
  supervisorId: number | null;
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

export interface SchemaConfig {
  legalBasis?: string;
  sheetTypes?: Partial<Record<SheetType, string>>;
  criteriaRules?: { maxAdditional?: number; allowCustom?: boolean };
  criteria: { code: string; name: string }[];
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

export interface StatsSummary {
  totalCycles: number;
  byStatus: Record<string, number>;
  byOutcome: Record<string, number>;
  byLevel: Record<string, number>;
  overdue: number;
  dueIn7Days: number;
  dueIn30Days: number;
  appeals: number;
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
