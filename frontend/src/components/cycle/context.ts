import type { Cycle, Note, Schema, User } from "@/lib/types";

/** Wspólne dane i uprawnienia przekazywane do zakładek cyklu. */
export interface CycleView {
  cycle: Cycle;
  schema: Schema | null;
  notes: Note[] | null;
  notesError: unknown;
  me: User;
  employee: User | undefined;
  isEvaluator: boolean;
  isEmployee: boolean;
  isOfficeHr: boolean;
  isOfficeDg: boolean;
  refresh: () => Promise<void>;
}
