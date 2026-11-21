"use client";

import { useState } from "react";
import { AiDisclaimer, Alert, Badge, Button, Card, Field, inputClass, MaskedPrompt, ModeBadge, ProblemAlert } from "@/components/ui";
import { api, coreAi } from "@/lib/api";
import { formatDate } from "@/lib/labels";
import type { CatalogCriterion, Cycle, SuggestCriteriaResponse } from "@/lib/types";
import type { CycleView } from "./context";

const DEFAULT_MAX_ADDITIONAL = 3;

export function CriteriaTab({ view }: { view: CycleView }) {
  const { cycle, schema, employee, isEvaluator, isEmployee, refresh } = view;
  const editable = isEvaluator && cycle.status === "DRAFT_CRITERIA";
  const catalog: CatalogCriterion[] = schema?.config.criteria ?? [];
  const mandatoryCodes = schema?.config.mandatoryBySheet?.[cycle.sheetType] ?? [];
  const maxAdditional = schema?.config.criteriaRules?.maxAdditional ?? DEFAULT_MAX_ADDITIONAL;
  const byCode = (code: string) => catalog.find((c) => c.code === code);
  const nameOf = (code: string) => byCode(code)?.name ?? code;

  const [selected, setSelected] = useState<string[]>(
    (cycle.criteria ?? []).filter((c) => !c.mandatory).map((c) => c.code),
  );
  const [interviewDate, setInterviewDate] = useState(cycle.criteriaInterviewDate ?? "");
  const [responsibilities, setResponsibilities] = useState("");
  const [suggestion, setSuggestion] = useState<SuggestCriteriaResponse | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);

  const toggle = (code: string) =>
    setSelected((s) => (s.includes(code) ? s.filter((c) => c !== code) : s.length < maxAdditional ? [...s, code] : s));

  const run = async (key: string, fn: () => Promise<unknown>, reload = true) => {
    setBusy(key);
    setError(null);
    setSaved(false);
    try {
      await fn();
      if (reload) {
        setSaved(true);
        await refresh();
      }
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const save = () =>
    run("save", () =>
      api<Cycle>(`/api/cycles/${cycle.id}/criteria`, {
        body: { additional: selected.map((code) => ({ code })), interviewDate: interviewDate || null },
      }),
    );
  const confirm = () => run("confirm", () => api<Cycle>(`/api/cycles/${cycle.id}/confirm-criteria`, { method: "POST" }));
  const suggest = () =>
    run(
      "ai",
      async () =>
        setSuggestion(
          await api<SuggestCriteriaResponse>(coreAi("suggest-criteria", cycle.id), {
            body: {
              position_title: employee?.positionTitle ?? "",
              department: employee?.department ?? "",
              sheet_type: cycle.sheetType,
              mandatory_codes: mandatoryCodes,
              responsibilities_summary: responsibilities,
            },
          }),
        ),
      false,
    );
  const takeAll = () =>
    setSelected((s) => [...new Set([...s, ...(suggestion?.suggestions.map((x) => x.code) ?? [])])].slice(0, maxAdditional));

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <div className="space-y-6">
        <Card title="Kryteria arkusza">
          {cycle.criteria?.length ? (
            <ol className="space-y-2">
              {cycle.criteria.map((c, i) => (
                <li key={c.code} className="rounded border border-slate-200 px-3 py-2 text-sm">
                  <div className="flex items-center justify-between gap-3">
                    <span>
                      <span className="mr-2 font-mono text-xs text-slate-600">{i + 1}.</span>
                      {c.name} <span className="font-mono text-xs text-slate-600">({c.code})</span>
                    </span>
                    <Badge tone={c.mandatory ? "blue" : "slate"}>{c.mandatory ? "obowiązkowe" : "dodatkowe"}</Badge>
                  </div>
                  <Behaviours criterion={byCode(c.code)} />
                </li>
              ))}
            </ol>
          ) : (
            <Alert tone="amber">Kryteria nie zostały jeszcze ustalone przez oceniającego.</Alert>
          )}

          <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
            <dt className="text-slate-600">Termin ustalenia kryteriów (§ 7)</dt>
            <dd>{formatDate(cycle.criteriaDeadline)}</dd>
            <dt className="text-slate-600">Rozmowa rozpoczynająca</dt>
            <dd>{formatDate(cycle.criteriaInterviewDate)}</dd>
            {cycle.criteriaConfirmedOn && (
              <>
                <dt className="text-slate-600">Potwierdzenie przez ocenianego</dt>
                <dd>{formatDate(cycle.criteriaConfirmedOn)}</dd>
              </>
            )}
          </dl>

          {cycle.status !== "DRAFT_CRITERIA" && cycle.criteria?.length ? (
            <p className="mt-4 text-sm text-slate-700">🔒 Kryteria zostały potwierdzone i są zablokowane do końca cyklu.</p>
          ) : null}

          {isEmployee && cycle.status === "DRAFT_CRITERIA" && (
            <div className="mt-4 space-y-2">
              <Button onClick={confirm} busy={busy === "confirm"} disabled={!cycle.criteria?.length}>
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
          <Card title="AI: Podpowiedz kryteria i oczekiwania" actions={suggestion && <ModeBadge mode={suggestion.mode} />}>
            <p className="mb-3 text-sm text-slate-700">
              Stanowisko: <strong>{employee?.positionTitle ?? "—"}</strong>
              {employee?.department ? ` · ${employee.department}` : ""}. Asystent proponuje wyłącznie kryteria z katalogu
              (zał. nr 1), spoza obowiązkowych — decyzja należy do oceniającego.
            </p>
            <Field label="Zakres obowiązków (opcjonalnie)" hint="Dane osobowe są maskowane przed wysłaniem do modelu.">
              <textarea className={inputClass} rows={3} value={responsibilities} onChange={(e) => setResponsibilities(e.target.value)} />
            </Field>
            <div className="mt-3">
              <Button variant="ai" onClick={suggest} busy={busy === "ai"}>
                AI: Podpowiedz kryteria i oczekiwania
              </Button>
            </div>
            {suggestion && (
              <div className="mt-4 space-y-3">
                <ul className="space-y-3">
                  {suggestion.suggestions.map((s) => {
                    const checked = selected.includes(s.code);
                    return (
                      <li key={s.code} className="rounded border border-navy-200 bg-gov-light p-3 text-sm">
                        <label className="flex items-start gap-2 font-medium">
                          <input
                            type="checkbox"
                            className="mt-0.5 h-4 w-4 accent-navy-700"
                            checked={checked}
                            disabled={!checked && selected.length >= maxAdditional}
                            onChange={() => toggle(s.code)}
                          />
                          <span>
                            <span className="font-mono text-xs">{s.code}</span> {s.name}
                          </span>
                        </label>
                        <p className="mt-1 text-slate-800">{s.reason}</p>
                        <p className="mt-2 text-xs font-semibold text-navy-900">Oczekiwania do rozmowy rozpoczynającej (§ 5 ust. 1 pkt 3):</p>
                        <ul className="list-disc pl-5 text-slate-800">
                          {s.expectations.map((e) => (
                            <li key={e}>{e}</li>
                          ))}
                        </ul>
                      </li>
                    );
                  })}
                </ul>
                <Button variant="secondary" onClick={takeAll}>
                  Zaznacz wszystkie sugerowane
                </Button>
                <MaskedPrompt prompt={suggestion.masked_prompt} />
                <AiDisclaimer text={suggestion.ai_disclaimer} />
              </div>
            )}
          </Card>
        )}
      </div>

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
            <div className="space-y-1">
              {catalog
                .filter((c) => !mandatoryCodes.includes(c.code))
                .map((c) => {
                  const checked = selected.includes(c.code);
                  const disabled = !checked && selected.length >= maxAdditional;
                  return (
                    <div key={c.code} className={`rounded px-2 py-1.5 text-sm ${disabled ? "text-slate-500" : "hover:bg-slate-50"}`}>
                      <label className="flex items-start gap-2">
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
                      <Behaviours criterion={c} />
                    </div>
                  );
                })}
            </div>
          </fieldset>
          <div className="mt-4">
            <Field label="Data rozmowy rozpoczynającej (§ 5 ust. 1)" hint={`Termin: ${formatDate(cycle.criteriaDeadline)}`}>
              <input type="date" className={inputClass} value={interviewDate} onChange={(e) => setInterviewDate(e.target.value)} />
            </Field>
          </div>
          <div className="mt-4">
            <Button onClick={save} busy={busy === "save"}>
              Zapisz kryteria
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}

/** Opisy zachowań z załącznika nr 1 — rozwijane, żeby lista kryteriów pozostała czytelna. */
function Behaviours({ criterion }: { criterion: CatalogCriterion | undefined }) {
  if (!criterion?.description?.length) return null;
  return (
    <details className="ml-6 mt-1 text-xs text-slate-700">
      <summary className="cursor-pointer select-none font-medium text-navy-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-600">
        Opis zachowań (zał. nr 1)
      </summary>
      <ul className="mt-1 list-disc space-y-0.5 pl-5">
        {criterion.description.map((d) => (
          <li key={d}>{d}</li>
        ))}
      </ul>
    </details>
  );
}
