"use client";

import { useState } from "react";
import { Alert, Badge, Button, Card, ProblemAlert, Spinner, inputClass } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/labels";
import type { InterviewPrepResponse, IprzFormType, IprzItem, IprzItemStatus } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";
import type { CycleView } from "./context";
import { fetchInterviewPrep } from "./InterviewTab";

const STATUS_LABEL: Record<IprzItemStatus, string> = { PLANNED: "zaplanowane", IN_PROGRESS: "w realizacji", DONE: "zrealizowane" };
const IPRZ_STATUS_LABEL = { DRAFT: "szkic", PROPOSED: "do zatwierdzenia", APPROVED: "zatwierdzony" } as const;

/** Forma rozwoju odgadywana z nazwy działania (do zmiany w edycji — decyduje oceniający). */
function formOf(action: string): IprzFormType {
  const a = action.toLowerCase();
  if (a.includes("mentor") || a.includes("coaching")) return "MENTORING";
  if (a.includes("szkoleni") || a.includes("warsztat") || a.includes("program rozwojowy")) return "SZKOLENIE";
  if (a.includes("samokształc") || a.includes("plan ")) return "SAMOKSZTALCENIE";
  if (a.includes("projekt") || a.includes("zespo")) return "PROJEKT";
  return "INNE";
}

/** Strukturalny IPRZ (art. 108): działania z formą, terminem i statusem; zatwierdza DG / kierownik urzędu, oceniany zapoznaje się. */
export function IprzPanel({ view }: { view: CycleView }) {
  const { cycle, isEvaluator, isEmployee, isOfficeHr, isOfficeDg, refresh } = view;
  const { userName } = useUser();
  const items = useAsync(() => api<IprzItem[]>(`/api/cycles/${cycle.id}/iprz-items`), [cycle.id]);
  const [prep, setPrep] = useState<InterviewPrepResponse | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const canEdit = (isEvaluator || isOfficeHr) && cycle.iprzStatus !== "APPROVED";
  const codeOf = (name: string) => (cycle.criteria ?? []).find((c) => c.name === name)?.code ?? null;
  const taken = new Set((items.data ?? []).map((i) => i.actionTitle));

  const run = async (key: string, fn: () => Promise<unknown>, reloadCycle = false) => {
    setBusy(key);
    setError(null);
    try {
      await fn();
      await items.reload();
      if (reloadCycle) await refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const add = (criterion: string, action: string) =>
    run(`add-${action}`, () =>
      api(`/api/cycles/${cycle.id}/iprz-items`, { body: { criterionCode: codeOf(criterion), actionTitle: action, formType: formOf(action) } }),
      true,
    );

  return (
    <Card
      title="Indywidualny program rozwoju zawodowego (IPRZ)"
      actions={<Badge tone={cycle.iprzStatus === "APPROVED" ? "green" : "amber"}>{IPRZ_STATUS_LABEL[cycle.iprzStatus]}</Badge>}
    >
      {items.loading && !items.data && <Spinner />}
      {items.data && items.data.length > 0 ? (
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Działania IPRZ</caption>
          <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
            <tr>
              <th className="py-2 pr-2">Działanie</th>
              <th className="py-2 pr-2">Forma</th>
              <th className="py-2 pr-2">Termin</th>
              <th className="py-2">Status</th>
            </tr>
          </thead>
          <tbody>
            {items.data.map((i) => (
              <tr key={i.id} className="border-b border-slate-100 last:border-0">
                <td className="py-2 pr-2">
                  {i.actionTitle}
                  {i.criterionCode && <span className="ml-1 font-mono text-xs text-slate-600">{i.criterionCode}</span>}
                </td>
                <td className="py-2 pr-2 text-xs">{i.formType.toLowerCase()}</td>
                <td className="py-2 pr-2 text-xs">{formatDate(i.targetDate)}</td>
                <td className="py-2">
                  {isEvaluator || isOfficeHr ? (
                    <select
                      aria-label={`Status: ${i.actionTitle}`}
                      className={`${inputClass} py-1 text-xs`}
                      value={i.status}
                      onChange={(e) =>
                        run(`st-${i.id}`, () =>
                          api(`/api/cycles/${cycle.id}/iprz-items/${i.id}`, { method: "PATCH", body: { status: e.target.value } }),
                        )
                      }
                    >
                      {(Object.keys(STATUS_LABEL) as IprzItemStatus[]).map((s) => (
                        <option key={s} value={s}>
                          {STATUS_LABEL[s]}
                        </option>
                      ))}
                    </select>
                  ) : (
                    STATUS_LABEL[i.status]
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        items.data && <p className="text-sm text-slate-700">Brak działań w IPRZ.</p>
      )}

      {canEdit && (
        <div className="mt-4 space-y-2 border-t border-slate-200 pt-3">
          <Button variant="ai" busy={busy === "prep"} onClick={() => run("prep", async () => setPrep(await fetchInterviewPrep(view, userName(cycle.employeeId), "EVALUATION")))}>
            Rekomendowane działania (kryteria ≤ 5 pkt)
          </Button>
          {prep && (
            <div className="space-y-2">
              {prep.development_catalog.map((d) => (
                <div key={d.criterion}>
                  <p className="text-xs font-semibold text-gov-text">{d.criterion}</p>
                  <div className="mt-1 flex flex-wrap gap-1">
                    {d.actions.map((a) => (
                      <button
                        key={a}
                        type="button"
                        disabled={taken.has(a) || busy !== null}
                        onClick={() => add(d.criterion, a)}
                        className="rounded-full px-3 py-1 text-xs font-semibold text-gov-blue ring-1 ring-inset ring-navy-200 hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue disabled:opacity-50"
                      >
                        {taken.has(a) ? "✓ " : "+ "}
                        {a}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="mt-4 flex flex-wrap gap-2">
        {isOfficeDg && cycle.iprzStatus !== "APPROVED" && (items.data?.length ?? 0) > 0 && (
          <Button busy={busy === "approve"} onClick={() => run("approve", () => api(`/api/cycles/${cycle.id}/iprz/approve`, { method: "POST" }), true)}>
            Zatwierdź IPRZ (art. 108)
          </Button>
        )}
        {isEmployee && cycle.iprzStatus === "APPROVED" && !cycle.iprzEmployeeAckAt && (
          <Button busy={busy === "ack"} onClick={() => run("ack", () => api(`/api/cycles/${cycle.id}/iprz/ack`, { method: "POST" }), true)}>
            Zapoznałem(-am) się z IPRZ
          </Button>
        )}
      </div>
      {cycle.iprzApprovedAt && (
        <p className="mt-2 text-xs text-slate-600">
          Zatwierdzono {formatDate(cycle.iprzApprovedAt)} ({userName(cycle.iprzApprovedBy)})
          {cycle.iprzEmployeeAckAt ? ` · zapoznanie ocenianego ${formatDate(cycle.iprzEmployeeAckAt)}` : ""}
        </p>
      )}
      {cycle.iprzStatus === "APPROVED" && canEdit === false && isEvaluator && (
        <Alert tone="slate">Zatwierdzony IPRZ — można aktualizować wyłącznie status realizacji działań.</Alert>
      )}
      <div className="mt-2">
        <ProblemAlert error={error} />
      </div>
    </Card>
  );
}
