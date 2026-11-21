"use client";

import { useState } from "react";
import { Alert, Badge, Button, Card, ProblemAlert } from "@/components/ui";
import { api } from "@/lib/api";
import type { Cycle } from "@/lib/types";
import type { CycleView } from "./context";

const DEFAULT_MAX_ADDITIONAL = 3;

export function CriteriaTab({ view }: { view: CycleView }) {
  const { cycle, schema, isEvaluator, isEmployee, refresh } = view;
  const editable = isEvaluator && cycle.status === "DRAFT_CRITERIA";
  const catalog = schema?.config.criteria ?? [];
  const mandatoryCodes = schema?.config.mandatoryBySheet?.[cycle.sheetType] ?? [];
  const maxAdditional = schema?.config.criteriaRules?.maxAdditional ?? DEFAULT_MAX_ADDITIONAL;
  const nameOf = (code: string) => catalog.find((c) => c.code === code)?.name ?? code;

  const [selected, setSelected] = useState<string[]>(
    (cycle.criteria ?? []).filter((c) => !c.mandatory).map((c) => c.code),
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);

  const toggle = (code: string) =>
    setSelected((s) => (s.includes(code) ? s.filter((c) => c !== code) : s.length < maxAdditional ? [...s, code] : s));

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      await fn();
      setSaved(true);
      await refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  const save = () =>
    run(() => api<Cycle>(`/api/cycles/${cycle.id}/criteria`, { body: { additional: selected.map((code) => ({ code })) } }));
  const confirm = () => run(() => api<Cycle>(`/api/cycles/${cycle.id}/confirm-criteria`, { method: "POST" }));

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card title="Kryteria arkusza">
        {cycle.criteria?.length ? (
          <ol className="space-y-2">
            {cycle.criteria.map((c, i) => (
              <li key={c.code} className="flex items-center justify-between gap-3 rounded border border-slate-200 px-3 py-2 text-sm">
                <span>
                  <span className="mr-2 font-mono text-xs text-slate-600">{i + 1}.</span>
                  {c.name} <span className="font-mono text-xs text-slate-600">({c.code})</span>
                </span>
                <Badge tone={c.mandatory ? "blue" : "slate"}>{c.mandatory ? "obowiązkowe" : "dodatkowe"}</Badge>
              </li>
            ))}
          </ol>
        ) : (
          <Alert tone="amber">Kryteria nie zostały jeszcze ustalone przez oceniającego.</Alert>
        )}

        {cycle.status !== "DRAFT_CRITERIA" && cycle.criteria?.length ? (
          <p className="mt-4 text-sm text-slate-700">🔒 Kryteria zostały potwierdzone i są zablokowane do końca cyklu.</p>
        ) : null}

        {isEmployee && cycle.status === "DRAFT_CRITERIA" && (
          <div className="mt-4 space-y-2">
            <Button onClick={confirm} busy={busy} disabled={!cycle.criteria?.length}>
              Potwierdź zapoznanie z kryteriami
            </Button>
            <p className="text-xs text-slate-600">Po potwierdzeniu kryteria zostaną zablokowane, a cykl przejdzie do etapu obserwacji.</p>
          </div>
        )}
        <div className="mt-4 space-y-2">
          <ProblemAlert error={error} />
          {saved && !error && <Alert tone="green">Zapisano.</Alert>}
        </div>
      </Card>

      {editable && (
        <Card title={`Wybór kryteriów (arkusz ${cycle.sheetType})`}>
          <p className="mb-2 text-sm font-medium text-slate-800">Obowiązkowe dla arkusza {cycle.sheetType} (§ 4):</p>
          <ul className="mb-4 flex flex-wrap gap-2">
            {mandatoryCodes.map((code) => (
              <li key={code}>
                <Badge tone="blue">
                  {code} · {nameOf(code)}
                </Badge>
              </li>
            ))}
          </ul>
          <fieldset>
            <legend className="mb-2 text-sm font-medium text-slate-800">
              Dodatkowe z katalogu (załącznik nr 1) — wybrano {selected.length}/{maxAdditional}
            </legend>
            <div className="grid gap-1 sm:grid-cols-2">
              {catalog
                .filter((c) => !mandatoryCodes.includes(c.code))
                .map((c) => {
                  const checked = selected.includes(c.code);
                  const disabled = !checked && selected.length >= maxAdditional;
                  return (
                    <label
                      key={c.code}
                      className={`flex items-start gap-2 rounded px-2 py-1.5 text-sm ${disabled ? "text-slate-500" : "hover:bg-slate-50"}`}
                    >
                      <input
                        type="checkbox"
                        className="mt-0.5 h-4 w-4 accent-navy-700"
                        checked={checked}
                        disabled={disabled}
                        onChange={() => toggle(c.code)}
                      />
                      <span>
                        <span className="font-mono text-xs">{c.code}</span> {c.name}
                      </span>
                    </label>
                  );
                })}
            </div>
          </fieldset>
          <div className="mt-4">
            <Button onClick={save} busy={busy}>
              Zapisz kryteria
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}
