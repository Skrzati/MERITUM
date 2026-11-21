"use client";

import { useState } from "react";
import { RoleGate } from "@/components/RoleGate";
import { Alert, Badge, Card, MaskedPrompt, ModeBadge, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import type { AiMode, AuditLogItem } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const AUDIT_LIMIT = 100;
const AI_PREFIX = "AI_";
const AI_MODES: AiMode[] = ["LIVE", "MOCK", "MOCK_FALLBACK"];

export default function AuditPage() {
  return (
    <RoleGate roles={["HR", "DG", "KPRM_ADMIN"]}>
      <Audit />
    </RoleGate>
  );
}

const isAi = (l: AuditLogItem) => l.action.startsWith(AI_PREFIX);

function aiMode(l: AuditLogItem): AiMode | null {
  const mode = l.payload?.mode;
  return typeof mode === "string" && (AI_MODES as string[]).includes(mode) ? (mode as AiMode) : null;
}

function maskedPrompt(l: AuditLogItem): string | null {
  const prompt = l.payload?.maskedPrompt;
  return typeof prompt === "string" && prompt ? prompt : null;
}

function Audit() {
  const { current, userName } = useUser();
  const logs = useAsync(() => api<AuditLogItem[]>(`/api/audit-logs?limit=${AUDIT_LIMIT}`), [current?.id]);
  const [aiOnly, setAiOnly] = useState(false);
  const all = logs.data ?? [];
  const visible = aiOnly ? all.filter(isAi) : all;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gov-text">Dziennik audytu</h1>
      <Alert tone="blue">
        Każda zmiana stanu cyklu, eksport i wywołanie AI są rejestrowane (AI Act art. 26). W audycie AI zapisywany jest wyłącznie
        prompt po maskowaniu PII.
      </Alert>
      <Card
        title={`Ostatnie zdarzenia (do ${AUDIT_LIMIT})`}
        actions={
          <label className="flex items-center gap-2 text-sm font-semibold text-gov-text">
            <input type="checkbox" className="h-4 w-4 accent-navy-700" checked={aiOnly} onChange={(e) => setAiOnly(e.target.checked)} />
            Tylko interakcje AI (AI Act art. 26) · {all.filter(isAi).length}
          </label>
        }
      >
        {logs.loading && <Spinner />}
        <ProblemAlert error={logs.error} />
        {logs.data && visible.length === 0 && <p className="text-sm text-slate-700">Brak zdarzeń.</p>}
        {visible.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Zdarzenia audytowe</caption>
              <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
                <tr>
                  <th className="py-2 pr-4">Czas</th>
                  <th className="py-2 pr-4">Zdarzenie</th>
                  <th className="py-2 pr-4">Wykonawca</th>
                  <th className="py-2 pr-4">Obiekt</th>
                  <th className="py-2">Szczegóły</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((l) => {
                  const mode = aiMode(l);
                  const prompt = maskedPrompt(l);
                  return (
                    <tr key={l.id} className="border-b border-slate-100 align-top last:border-0">
                      <td className="whitespace-nowrap py-2 pr-4">{new Date(l.createdAt).toLocaleString("pl-PL")}</td>
                      <td className="py-2 pr-4">
                        <div className="flex flex-wrap gap-1">
                          <Badge tone={isAi(l) ? "blue" : "slate"}>{l.action}</Badge>
                          {mode && <ModeBadge mode={mode} />}
                        </div>
                      </td>
                      <td className="py-2 pr-4">{userName(l.actorId)}</td>
                      <td className="py-2 pr-4">
                        {l.entityType}
                        {l.entityId !== null ? ` #${l.entityId}` : ""}
                      </td>
                      <td className="w-1/2 space-y-2 py-2">
                        {prompt && <MaskedPrompt prompt={prompt} />}
                        {l.payload && Object.keys(l.payload).length > 0 && (
                          <details>
                            <summary className="cursor-pointer text-sm font-semibold text-gov-blue">Payload (JSON)</summary>
                            <pre className="mt-1 max-h-72 overflow-auto whitespace-pre-wrap rounded bg-gov-section p-2 font-mono text-xs">
                              {JSON.stringify(l.payload, null, 2)}
                            </pre>
                          </details>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
