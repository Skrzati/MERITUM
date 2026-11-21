import { PROCESS_STEPS } from "@/lib/labels";
import type { CycleStatus } from "@/lib/types";

const ORDER: Record<CycleStatus, number> = {
  DRAFT_CRITERIA: 0,
  CRITERIA_CONFIRMED: 1,
  OBSERVATION: 1,
  EVALUATION_DRAFT: 2,
  AWAITING_ACK: 3,
  COMPLETED: 4,
  APPEALED: 4,
  APPEALED_TO_COURT: 4,
};

export function ProgressSteps({ status }: { status: CycleStatus }) {
  const position = ORDER[status];
  return (
    <ol className="flex flex-wrap items-center gap-2" aria-label="Etapy procesu oceny">
      {PROCESS_STEPS.map((step, i) => {
        const state = i < position || (i === position && status === "COMPLETED") ? "done" : i === position ? "current" : "todo";
        return (
          <li key={step.status} className="flex items-center gap-2" aria-current={state === "current" ? "step" : undefined}>
            <span
              className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold ${
                state === "done" ? "bg-green-700 text-white" : state === "current" ? "bg-navy-700 text-white" : "bg-slate-200 text-slate-700"
              }`}
            >
              {state === "done" ? "✓" : i + 1}
            </span>
            <span className={`text-sm ${state === "current" ? "font-semibold text-navy-900" : "text-slate-700"}`}>{step.label}</span>
            {i < PROCESS_STEPS.length - 1 && <span aria-hidden className="mx-1 h-px w-6 bg-slate-300" />}
          </li>
        );
      })}
      {status === "APPEALED" && (
        <li className="ml-2 rounded bg-red-50 px-2 py-0.5 text-xs font-semibold text-red-900 ring-1 ring-red-300">Sprzeciw u DG</li>
      )}
    </ol>
  );
}
