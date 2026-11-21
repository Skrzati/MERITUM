"use client";

import { RoleGate } from "@/components/RoleGate";
import { Alert, Badge, Card, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import type { AuditLogItem } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const AUDIT_LIMIT = 100;

export default function AuditPage() {
  return (
    <RoleGate roles={["HR", "DG", "KPRM_ADMIN"]}>
      <Audit />
    </RoleGate>
  );
}

function Audit() {
  const { current, userName } = useUser();
  const logs = useAsync(() => api<AuditLogItem[]>(`/api/audit-logs?limit=${AUDIT_LIMIT}`), [current?.id]);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gov-text">Dziennik audytu</h1>
      <Alert tone="blue">
        Każda zmiana stanu cyklu, eksport i wywołanie AI są rejestrowane (AI Act art. 26). W audycie AI zapisywany jest wyłącznie
        prompt po maskowaniu PII.
      </Alert>
      <Card title={`Ostatnie zdarzenia (do ${AUDIT_LIMIT})`}>
        {logs.loading && <Spinner />}
        <ProblemAlert error={logs.error} />
        {logs.data && (
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
                {logs.data.map((l) => (
                  <tr key={l.id} className="border-b border-slate-100 align-top last:border-0">
                    <td className="whitespace-nowrap py-2 pr-4">{new Date(l.createdAt).toLocaleString("pl-PL")}</td>
                    <td className="py-2 pr-4">
                      <Badge tone={l.action.startsWith("AI_") ? "blue" : "slate"}>{l.action}</Badge>
                    </td>
                    <td className="py-2 pr-4">{userName(l.actorId)}</td>
                    <td className="py-2 pr-4">
                      {l.entityType}
                      {l.entityId !== null ? ` #${l.entityId}` : ""}
                    </td>
                    <td className="py-2">
                      {l.payload && Object.keys(l.payload).length > 0 && (
                        <details>
                          <summary className="cursor-pointer text-gov-blue">pokaż</summary>
                          <pre className="mt-1 max-w-xl overflow-auto whitespace-pre-wrap rounded bg-gov-section p-2 font-mono text-xs">
                            {JSON.stringify(l.payload, null, 2)}
                          </pre>
                        </details>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
