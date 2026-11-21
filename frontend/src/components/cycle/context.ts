import type { Note, Schema, User } from "@/lib/types";
import type { CycleDetails, SchemaConfigDetails, UserDetails } from "./contracts";

/** Wspólne dane i uprawnienia przekazywane do zakładek cyklu. */
export interface CycleView {
  cycle: CycleDetails;
  schema: (Schema & { config: SchemaConfigDetails }) | null;
  notes: Note[] | null;
  notesError: unknown;
  me: User;
  employee: UserDetails | undefined;
  isEvaluator: boolean;
  isEmployee: boolean;
  isOfficeHr: boolean;
  isOfficeDg: boolean;
  refresh: () => Promise<void>;
}
