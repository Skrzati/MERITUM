import type { Cycle, CycleStatus, EscalationLevel, Outcome, PerformanceLevel, Role, SheetType, User } from "./types";

export const ROLE_LABEL: Record<Role, string> = {
  EVALUATOR: "Kierownik",
  EVALUATED: "Oceniany",
  HR: "Kadry",
  DG: "Dyrektor Generalny",
  KPRM_ADMIN: "Admin KPRM",
};

export const ROLE_ORDER: Role[] = ["EVALUATOR", "EVALUATED", "HR", "DG", "KPRM_ADMIN"];

export const STATUS_LABEL: Record<CycleStatus, string> = {
  DRAFT_CRITERIA: "Ustalanie kryteriów",
  CRITERIA_CONFIRMED: "Kryteria potwierdzone",
  OBSERVATION: "Obserwacja",
  EVALUATION_DRAFT: "Szkic oceny",
  AWAITING_ACK: "Oczekuje na zapoznanie",
  COMPLETED: "Zakończona",
  APPEALED: "Sprzeciw",
};

export const STATUS_TONE: Record<CycleStatus, Tone> = {
  DRAFT_CRITERIA: "slate",
  CRITERIA_CONFIRMED: "blue",
  OBSERVATION: "blue",
  EVALUATION_DRAFT: "amber",
  AWAITING_ACK: "amber",
  COMPLETED: "green",
  APPEALED: "red",
};

export const LEVEL_LABEL: Record<PerformanceLevel, string> = {
  SIGNIFICANTLY_BELOW: "znacznie poniżej oczekiwań",
  BELOW: "poniżej oczekiwań",
  MEETS: "na poziomie oczekiwań",
  ABOVE: "powyżej oczekiwań",
  SIGNIFICANTLY_ABOVE: "znacznie powyżej oczekiwań",
};

export const OUTCOME_LABEL: Record<Outcome, string> = { POSITIVE: "Pozytywna", NEGATIVE: "Negatywna" };

export const SCALE: { value: number; label: string }[] = [
  { value: 1, label: "znacznie poniżej" },
  { value: 3, label: "poniżej" },
  { value: 5, label: "na poziomie" },
  { value: 7, label: "powyżej" },
  { value: 9, label: "znacznie powyżej" },
];

/** Kroki procesu pokazywane na pasku postępu. */
export const PROCESS_STEPS: { status: CycleStatus; label: string }[] = [
  { status: "DRAFT_CRITERIA", label: "Kryteria" },
  { status: "OBSERVATION", label: "Obserwacja" },
  { status: "EVALUATION_DRAFT", label: "Ocena" },
  { status: "AWAITING_ACK", label: "Zapoznanie" },
  { status: "COMPLETED", label: "Zakończona" },
];

export type Tone = "slate" | "blue" | "amber" | "green" | "red";

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso.length === 10 ? `${iso}T00:00:00` : iso);
  return d.toLocaleDateString("pl-PL", { day: "2-digit", month: "2-digit", year: "numeric" });
}

export function today(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

/** Podgląd średniej w UI (1 miejsce po przecinku). Wiążący wynik wylicza silnik reguł backendu. */
export function previewAverage(scores: (number | undefined)[]): string | null {
  if (scores.length === 0 || scores.some((s) => s === undefined)) return null;
  const sum = (scores as number[]).reduce((a, b) => a + b, 0);
  const n = scores.length;
  const tenths = Math.floor((20 * sum + n) / (2 * n)); // zaokrąglenie HALF_UP na liczbach całkowitych
  return (tenths / 10).toFixed(1);
}

/** Najwyższy stopień służbowy w służbie cywilnej — wniosek o kolejny stopień jest bezprzedmiotowy. */
export const MAX_CIVIL_SERVICE_GRADE = 9;

/** Status terminu oceny: w terminie / zbliża się (≤ 30 dni) / po terminie. */
export const DUE_SOON_DAYS = 30;
export function dueState(c: Pick<Cycle, "dueDate" | "overdueDays" | "status" | "evaluatedOn">, todayIso: string): { tone: Tone; label: string } {
  if (c.overdueDays > 0) return { tone: "red", label: `po terminie ${c.overdueDays} dni` };
  if (c.status === "COMPLETED" || c.status === "APPEALED" || c.evaluatedOn) return { tone: "green", label: "ocena sporządzona" };
  const days = Math.round((Date.parse(c.dueDate) - Date.parse(todayIso)) / 86_400_000);
  return days <= DUE_SOON_DAYS ? { tone: "amber", label: `zbliża się (${days} dni)` } : { tone: "green", label: "w terminie" };
}

/** Etykieta roli z uwzględnieniem małego urzędu: rola DG bez stanowiska dyrektora generalnego = kierownik urzędu. */
export function roleLabel(user: Pick<User, "role" | "officeHasDgRole"> | null | undefined): string {
  if (!user) return "—";
  return user.role === "DG" && user.officeHasDgRole === false ? "Kierownik urzędu" : ROLE_LABEL[user.role];
}

export function escalationLabel(level: EscalationLevel, officeHasDgRole = true): string {
  const labels: Record<EscalationLevel, string> = {
    OCENIAJACY: "Oceniający (1–7 dni)",
    ZASTEPCA: "Zastępca (8–14 dni)",
    KADRY: "Kadry (15–30 dni)",
    DYREKTOR_GENERALNY: officeHasDgRole ? "Dyrektor Generalny (> 30 dni)" : "Kierownik urzędu (> 30 dni)",
  };
  return labels[level];
}

export const SHEET_LABEL: Record<SheetType, string> = {
  A: "A — kierownik urzędu",
  B: "B — bezpośrednie kierowanie pracownikami",
  C: "C — pozostałe stanowiska",
};

/** § 3 rozporządzenia: kreator arkusza na podstawie dwóch pytań. */
export function sheetFromAnswers(isHeadOfOffice: boolean, managesPeople: boolean): SheetType {
  if (isHeadOfOffice) return "A";
  return managesPeople ? "B" : "C";
}
