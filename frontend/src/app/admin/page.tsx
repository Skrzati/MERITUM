"use client";

import { useEffect, useMemo, useState } from "react";
import { RoleGate } from "@/components/RoleGate";
import { Alert, Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api, download } from "@/lib/api";
import { formatDate, today } from "@/lib/labels";
import type { Cycle, Schema, SchemaConfig, SheetType } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const SHEETS: SheetType[] = ["A", "B", "C"];
const MAX_ADDITIONAL_LIMIT = 3;
const DEFAULT_MAX_ADDITIONAL = 3;
const CODE_PREFIX = "K";
const CODE_DIGITS = 2;

/** Wiersz kreatora — jedno kryterium katalogu z przypisaniem „obowiązkowe” dla arkuszy A/B/C. */
interface CriterionRow {
  code: string;
  name: string;
  /** Opisy zachowań — jedna linia = jeden opis. */
  description: string;
  mandatory: Record<SheetType, boolean>;
  added: boolean;
}

interface Draft {
  versionCode: string;
  effectiveFrom: string;
  periodMonths: number;
  maxAdditional: number;
  allowCustom: boolean;
  rows: CriterionRow[];
}

export default function AdminPage() {
  return (
    <RoleGate roles={["KPRM_ADMIN"]}>
      <SchemaAdmin />
    </RoleGate>
  );
}

function draftFrom(schema: Schema): Draft {
  const c = schema.config;
  return {
    versionCode: `${schema.versionCode}-nowelizacja`,
    effectiveFrom: today(),
    periodMonths: schema.periodMonths,
    maxAdditional: c.criteriaRules?.maxAdditional ?? DEFAULT_MAX_ADDITIONAL,
    allowCustom: c.criteriaRules?.allowCustom ?? false,
    rows: (c.criteria ?? []).map((k) => ({
      code: k.code,
      name: k.name,
      description: (k.description ?? []).join("\n"),
      mandatory: { A: false, B: false, C: false, ...Object.fromEntries(SHEETS.map((s) => [s, !!c.mandatoryBySheet?.[s]?.includes(k.code)])) },
      added: false,
    })),
  };
}

/** Konfiguracja wysyłana do backendu — pozostałe klucze (np. podstawa prawna) przejmowane z wersji bazowej. */
function configFrom(base: SchemaConfig, d: Draft): SchemaConfig {
  return {
    ...base,
    criteriaRules: { ...base.criteriaRules, maxAdditional: d.maxAdditional, allowCustom: d.allowCustom },
    criteria: d.rows.map((r) => {
      const description = r.description.split("\n").map((l) => l.trim()).filter(Boolean);
      return description.length ? { code: r.code, name: r.name.trim(), description } : { code: r.code, name: r.name.trim() };
    }),
    mandatoryBySheet: Object.fromEntries(SHEETS.map((s) => [s, d.rows.filter((r) => r.mandatory[s]).map((r) => r.code)])),
  };
}

function nextCode(rows: CriterionRow[]): string {
  const max = rows.reduce((m, r) => Math.max(m, Number(r.code.replace(/\D/g, "")) || 0), 0);
  return `${CODE_PREFIX}${String(max + 1).padStart(CODE_DIGITS, "0")}`;
}

function SchemaAdmin() {
  const { current } = useUser();
  const schemas = useAsync(() => api<Schema[]>("/api/admin/schemas"), [current?.id]);
  const cycles = useAsync(() => api<Cycle[]>("/api/cycles"), [current?.id]);
  const cyclesBySchema = (id: number) => (cycles.data ?? []).filter((c) => c.schemaVersionId === id).length;

  const latest = schemas.data?.[0];
  const [draft, setDraft] = useState<Draft | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [created, setCreated] = useState<Schema | null>(null);

  useEffect(() => {
    if (latest && !draft) setDraft(draftFrom(latest));
  }, [latest, draft]);

  const config = useMemo(() => (latest && draft ? configFrom(latest.config, draft) : null), [latest, draft]);

  const update = (patch: Partial<Draft>) => setDraft((d) => (d ? { ...d, ...patch } : d));
  const updateRow = (code: string, patch: Partial<CriterionRow>) =>
    setDraft((d) => (d ? { ...d, rows: d.rows.map((r) => (r.code === code ? { ...r, ...patch } : r)) } : d));
  const addRow = () =>
    setDraft((d) =>
      d ? { ...d, rows: [...d.rows, { code: nextCode(d.rows), name: "", description: "", mandatory: { A: false, B: false, C: false }, added: true }] } : d,
    );
  const removeRow = (code: string) => setDraft((d) => (d ? { ...d, rows: d.rows.filter((r) => r.code !== code) } : d));

  const downloadPackage = async (s: Schema) => {
    setBusy(`pkg-${s.id}`);
    setError(null);
    try {
      await download(`/api/admin/schemas/${s.id}/package`, `meritum-rules-${s.versionCode}.json`);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const create = async () => {
    if (!draft || !config) return;
    setBusy("create");
    setError(null);
    setCreated(null);
    try {
      setCreated(
        await api<Schema>("/api/admin/schemas", {
          body: { versionCode: draft.versionCode.trim(), effectiveFrom: draft.effectiveFrom, periodMonths: draft.periodMonths, config },
        }),
      );
      await schemas.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const incomplete = !draft || !draft.versionCode.trim() || draft.periodMonths < 1 || draft.rows.some((r) => !r.name.trim());

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gov-text">Prawo jako kod</h1>
        <p className="text-sm text-slate-700">Wersjonowane schematy oceny. Istniejące cykle zachowują swoją wersję schematu (§ 13).</p>
      </div>

      <Card title="Wersje schematu">
        {schemas.loading && <Spinner />}
        <ProblemAlert error={schemas.error} />
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Wersje schematu</caption>
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
              <tr>
                <th className="py-2 pr-3">Wersja</th>
                <th className="py-2 pr-3">Obowiązuje od</th>
                <th className="py-2 pr-3">Okres oceny</th>
                <th className="py-2 pr-3">Katalog kryteriów</th>
                <th className="py-2 pr-3">Obowiązkowe A / B / C</th>
                <th className="py-2 pr-3">Przypisane cykle</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody>
              {(schemas.data ?? []).map((s) => {
                const m = s.config.mandatoryBySheet ?? {};
                return (
                  <tr key={s.id} className="border-b border-slate-100 last:border-0">
                    <td className="py-2 pr-3">
                      <Badge tone="blue">{s.versionCode}</Badge>
                    </td>
                    <td className="py-2 pr-3">{formatDate(s.effectiveFrom)}</td>
                    <td className="py-2 pr-3">{s.periodMonths} mies.</td>
                    <td className="py-2 pr-3">{s.config.criteria?.length ?? 0}</td>
                    <td className="py-2 pr-3">
                      {m.A?.length ?? 0} / {m.B?.length ?? 0} / {m.C?.length ?? 0}
                    </td>
                    <td className="py-2 pr-3">{cyclesBySchema(s.id)}</td>
                    <td className="py-2 text-right">
                      <Button variant="secondary" onClick={() => downloadPackage(s)} busy={busy === `pkg-${s.id}`}>
                        Pobierz pakiet reguł (.json)
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>

      {draft && latest && (
        <Card title="Kreator nowej wersji schematu">
          <p className="mb-4 text-sm text-slate-700">
            Formularz wypełniony na podstawie wersji <Badge tone="blue">{latest.versionCode}</Badge>. Zmiany obejmą wyłącznie nowe
            cykle; serwer waliduje katalog i kryteria obowiązkowe przed zapisem.
          </p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            <Field label="Kod wersji">
              <input className={inputClass} value={draft.versionCode} onChange={(e) => update({ versionCode: e.target.value })} />
            </Field>
            <Field label="Data wejścia w życie">
              <input type="date" className={inputClass} value={draft.effectiveFrom} onChange={(e) => update({ effectiveFrom: e.target.value })} />
            </Field>
            <Field label="Okres oceny (miesiące)" hint={`Obecnie: ${latest.periodMonths} mies.`}>
              <input
                type="number"
                min={1}
                className={inputClass}
                value={draft.periodMonths}
                onChange={(e) => update({ periodMonths: Number(e.target.value) })}
              />
            </Field>
            <Field label="Maks. kryteriów dodatkowych">
              <select className={inputClass} value={draft.maxAdditional} onChange={(e) => update({ maxAdditional: Number(e.target.value) })}>
                {Array.from({ length: MAX_ADDITIONAL_LIMIT + 1 }, (_, n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </Field>
            <label className="flex items-center gap-2 self-end pb-2 text-sm font-semibold text-gov-text">
              <input
                type="checkbox"
                className="h-4 w-4 accent-navy-700"
                checked={draft.allowCustom}
                onChange={(e) => update({ allowCustom: e.target.checked })}
              />
              Zezwól na kryteria własne
            </label>
          </div>

          <div className="mt-6 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="mb-2 text-left text-sm font-semibold text-gov-text">
                Katalog kryteriów ({draft.rows.length}) · obowiązkowe A / B / C:{" "}
                {SHEETS.map((s) => draft.rows.filter((r) => r.mandatory[s]).length).join(" / ")}
              </caption>
              <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
                <tr>
                  <th className="py-2 pr-3">Kod</th>
                  <th className="py-2 pr-3">Nazwa</th>
                  <th className="py-2 pr-3">Opis zachowań (jedna linia = jeden opis)</th>
                  <th className="py-2 pr-3 text-center">Obowiązkowe dla arkusza</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {draft.rows.map((r) => (
                  <tr key={r.code} className="border-b border-slate-100 align-top last:border-0">
                    <td className="py-2 pr-3 font-mono text-xs">{r.code}</td>
                    <td className="py-2 pr-3">
                      <input
                        aria-label={`Nazwa kryterium ${r.code}`}
                        className={inputClass}
                        value={r.name}
                        onChange={(e) => updateRow(r.code, { name: e.target.value })}
                      />
                    </td>
                    <td className="py-2 pr-3">
                      <textarea
                        aria-label={`Opis zachowań ${r.code}`}
                        className={`${inputClass} text-xs`}
                        rows={2}
                        value={r.description}
                        onChange={(e) => updateRow(r.code, { description: e.target.value })}
                      />
                    </td>
                    <td className="py-2 pr-3">
                      <div className="flex justify-center gap-3">
                        {SHEETS.map((s) => (
                          <label key={s} className="flex flex-col items-center text-xs font-semibold">
                            <input
                              type="checkbox"
                              aria-label={`${r.code} obowiązkowe dla arkusza ${s}`}
                              className="h-4 w-4 accent-navy-700"
                              checked={r.mandatory[s]}
                              onChange={(e) => updateRow(r.code, { mandatory: { ...r.mandatory, [s]: e.target.checked } })}
                            />
                            [{s}]
                          </label>
                        ))}
                      </div>
                    </td>
                    <td className="py-2 text-right">
                      {r.added && (
                        <Button variant="secondary" onClick={() => removeRow(r.code)} aria-label={`Usuń kryterium ${r.code}`}>
                          Usuń
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-3">
            <Button variant="secondary" onClick={addRow}>
              + Dodaj kryterium
            </Button>
          </div>

          <details className="mt-6 rounded border border-dashed border-slate-400">
            <summary className="cursor-pointer px-3 py-2 text-sm font-semibold text-gov-blue focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue">
              Podgląd techniczny JSON (dla IT)
            </summary>
            <pre className="max-h-96 overflow-auto whitespace-pre-wrap border-t border-slate-200 bg-gov-section px-3 py-2 font-mono text-xs">
              {JSON.stringify(
                { versionCode: draft.versionCode, effectiveFrom: draft.effectiveFrom, periodMonths: draft.periodMonths, config },
                null,
                2,
              )}
            </pre>
          </details>

          <div className="mt-4 space-y-3">
            <Button onClick={create} busy={busy === "create"} disabled={incomplete}>
              Utwórz wersję schematu
            </Button>
            {incomplete && <p className="text-xs text-slate-600">Uzupełnij kod wersji, okres oceny i nazwy wszystkich kryteriów.</p>}
            <ProblemAlert error={error} />
            {created && (
              <Alert tone="green" title={`Utworzono ${created.versionCode} (${created.periodMonths} mies.)`}>
                Istniejące cykle pozostają przy dotychczasowej wersji — zgodnie z § 13 nowa wersja dotyczy wyłącznie nowych cykli.
              </Alert>
            )}
          </div>
        </Card>
      )}
    </div>
  );
}
