"use client";

import { useState } from "react";
import { Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import { formatDate, today } from "@/lib/labels";
import type { Substitution, User } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

/** Zastępstwa oceniających: aktywny zastępca prowadzi aktywne cykle zastępowanego (adnotacja w audycie). */
export function SubstitutionsCard({ employees }: { employees: User[] }) {
  const { current, userName } = useUser();
  const list = useAsync(() => api<Substitution[]>("/api/substitutions"), [current?.id]);
  const evaluators = employees.filter((u) => u.role === "EVALUATOR" || u.role === "DG");
  const [evaluatorId, setEvaluatorId] = useState("");
  const [deputyId, setDeputyId] = useState("");
  const [start, setStart] = useState(today());
  const [end, setEnd] = useState(today());
  const [reason, setReason] = useState("Długotrwała nieobecność oceniającego");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  const run = async (key: string, fn: () => Promise<unknown>) => {
    setBusy(key);
    setError(null);
    try {
      await fn();
      await list.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const create = () =>
    run("create", () =>
      api("/api/substitutions", {
        body: { evaluatorId: Number(evaluatorId), deputyId: Number(deputyId), startDate: start, endDate: end, reason },
      }),
    );

  return (
    <Card title="Zastępstwa oceniających">
      <p className="mb-3 text-sm text-slate-700">
        W okresie zastępstwa zastępca widzi aktywne cykle zastępowanego, dodaje notatki, prowadzi rozmowy i sporządza ocenę;
        dostaje też powiadomienia. Każda akcja ma w audycie adnotację <code>substituteFor</code>.
      </p>
      {list.loading && !list.data && <Spinner />}
      {list.data && list.data.length > 0 && (
        <ul className="mb-4 space-y-2 text-sm">
          {list.data.map((s) => (
            <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 rounded border border-slate-200 p-2">
              <span>
                <Badge tone={s.activeToday ? "green" : "slate"}>{s.activeToday ? "trwa" : s.endDate < today() ? "zakończone" : "zaplanowane"}</Badge>{" "}
                <strong>{userName(s.deputyId)}</strong> zastępuje <strong>{userName(s.evaluatorId)}</strong> ·{" "}
                {formatDate(s.startDate)} – {formatDate(s.endDate)} · {s.reason}
              </span>
              {s.endDate >= today() && (
                <Button variant="secondary" busy={busy === `end-${s.id}`} onClick={() => run(`end-${s.id}`, () => api(`/api/substitutions/${s.id}`, { method: "DELETE" }))}>
                  Zakończ
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Zastępowany oceniający">
          <select className={inputClass} value={evaluatorId} onChange={(e) => setEvaluatorId(e.target.value)}>
            <option value="">— wybierz —</option>
            {evaluators.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Zastępca">
          <select className={inputClass} value={deputyId} onChange={(e) => setDeputyId(e.target.value)}>
            <option value="">— wybierz —</option>
            {evaluators
              .filter((u) => String(u.id) !== evaluatorId)
              .map((u) => (
                <option key={u.id} value={u.id}>
                  {u.fullName}
                </option>
              ))}
          </select>
        </Field>
        <Field label="Od">
          <input type="date" className={inputClass} value={start} onChange={(e) => setStart(e.target.value)} />
        </Field>
        <Field label="Do">
          <input type="date" className={inputClass} value={end} onChange={(e) => setEnd(e.target.value)} />
        </Field>
      </div>
      <div className="mt-3">
        <Field label="Powód">
          <input className={inputClass} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
      </div>
      <div className="mt-3 space-y-2">
        <Button onClick={create} busy={busy === "create"} disabled={!evaluatorId || !deputyId || !reason.trim()}>
          Ustaw zastępstwo
        </Button>
        <ProblemAlert error={error} />
      </div>
    </Card>
  );
}
