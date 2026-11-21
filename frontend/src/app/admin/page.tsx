"use client";

import { useEffect, useState } from "react";
import { RoleGate } from "@/components/RoleGate";
import { Alert, Badge, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import { formatDate, today } from "@/lib/labels";
import type { Cycle, Schema, SchemaConfig } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

export default function AdminPage() {
  return (
    <RoleGate roles={["KPRM_ADMIN"]}>
      <SchemaAdmin />
    </RoleGate>
  );
}

function SchemaAdmin() {
  const { current } = useUser();
  const schemas = useAsync(() => api<Schema[]>("/api/admin/schemas"), [current?.id]);
  const cycles = useAsync(() => api<Cycle[]>("/api/cycles"), [current?.id]);
  const cyclesBySchema = (id: number) => (cycles.data ?? []).filter((c) => c.schemaVersionId === id).length;

  const [versionCode, setVersionCode] = useState("v2");
  const [effectiveFrom, setEffectiveFrom] = useState(today());
  const [periodMonths, setPeriodMonths] = useState("12");
  const [configText, setConfigText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [created, setCreated] = useState<Schema | null>(null);

  const latest = schemas.data?.[0];
  useEffect(() => {
    if (latest && !configText) setConfigText(JSON.stringify(latest.config, null, 2));
  }, [latest, configText]);

  const create = async () => {
    setBusy(true);
    setError(null);
    setCreated(null);
    try {
      let config: SchemaConfig;
      try {
        config = JSON.parse(configText) as SchemaConfig;
      } catch {
        throw new Error("Konfiguracja nie jest poprawnym JSON-em");
      }
      setCreated(
        await api<Schema>("/api/admin/schemas", {
          body: { versionCode, effectiveFrom, periodMonths: Number(periodMonths), config },
        }),
      );
      await schemas.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-navy-900">Prawo jako kod</h1>
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
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="Utwórz nową wersję schematu">
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Kod wersji">
            <input className={inputClass} value={versionCode} onChange={(e) => setVersionCode(e.target.value)} />
          </Field>
          <Field label="Obowiązuje od">
            <input type="date" className={inputClass} value={effectiveFrom} onChange={(e) => setEffectiveFrom(e.target.value)} />
          </Field>
          <Field label="Okres oceny (miesiące)">
            <input type="number" min={1} className={inputClass} value={periodMonths} onChange={(e) => setPeriodMonths(e.target.value)} />
          </Field>
        </div>
        <div className="mt-4">
          <Field label="Konfiguracja (JSON) — skopiowana z najnowszej wersji" hint="Walidowana po stronie serwera: katalog kryteriów i kryteria obowiązkowe dla A, B, C.">
            <textarea className={`${inputClass} font-mono text-xs`} rows={14} value={configText} onChange={(e) => setConfigText(e.target.value)} />
          </Field>
        </div>
        <div className="mt-4 space-y-3">
          <Button onClick={create} busy={busy}>
            Utwórz wersję schematu
          </Button>
          <ProblemAlert error={error} />
          {created && (
            <Alert tone="green" title={`Utworzono ${created.versionCode} (${created.periodMonths} mies.)`}>
              Istniejące cykle pozostają przy dotychczasowej wersji — zgodnie z § 13 nowa wersja dotyczy wyłącznie nowych cykli.
            </Alert>
          )}
        </div>
      </Card>
    </div>
  );
}
