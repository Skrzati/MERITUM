"use client";

import Link from "next/link";
import { useMemo } from "react";
import { Badge, Card, ProblemAlert, Spinner } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import { formatDate, ROLE_LABEL, STATUS_LABEL, STATUS_TONE, today } from "@/lib/labels";
import type { Cycle, Schema } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

export default function DashboardPage() {
  const { current, userName, loading: userLoading, error: userError } = useUser();
  const cycles = useAsync(async () => (current ? api<Cycle[]>("/api/cycles") : []), [current?.id]);
  const schemaIds = useMemo(() => [...new Set((cycles.data ?? []).map((c) => c.schemaVersionId))], [cycles.data]);
  const schemas = useAsync(
    async () => Promise.all(schemaIds.map((id) => api<Schema>(`/api/schemas/${id}`))),
    [schemaIds.join(","), current?.id],
  );
  const versionOf = (id: number) => schemas.data?.find((s) => s.id === id)?.versionCode ?? `#${id}`;

  if (userLoading) return <Spinner />;
  if (userError) return <ProblemAlert error={`Brak połączenia z backend-core: ${userError}`} />;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-navy-900">Pulpit</h1>
        <p className="text-sm text-slate-700">
          Cykle ocen widoczne dla roli <strong>{current ? ROLE_LABEL[current.role] : "—"}</strong>.
        </p>
      </div>

      <Card title="Cykle oceny okresowej">
        {cycles.loading && <Spinner />}
        <ProblemAlert error={cycles.error} />
        {cycles.data && cycles.data.length === 0 && <p className="text-sm text-slate-700">Brak cykli do wyświetlenia.</p>}
        {cycles.data && cycles.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Lista cykli oceny</caption>
              <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
                <tr>
                  <th className="py-2 pr-4">Oceniany</th>
                  <th className="py-2 pr-4">Oceniający</th>
                  <th className="py-2 pr-4">Arkusz</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2 pr-4">Termin oceny</th>
                  <th className="py-2 pr-4">Schemat</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {cycles.data.map((c) => (
                  <tr key={c.id} className="border-b border-slate-100 last:border-0">
                    <td className="py-3 pr-4 font-medium">
                      {userName(c.employeeId)}
                      {c.employeeId === current?.id && <span className="ml-2 text-xs text-slate-600">(Ty)</span>}
                    </td>
                    <td className="py-3 pr-4">{userName(c.evaluatorId)}</td>
                    <td className="py-3 pr-4">{c.sheetType}</td>
                    <td className="py-3 pr-4">
                      <Badge tone={STATUS_TONE[c.status]}>{STATUS_LABEL[c.status]}</Badge>
                    </td>
                    <td className="py-3 pr-4">
                      {formatDate(c.dueDate)}
                      {c.dueDate < today() && c.status !== "COMPLETED" && (
                        <span className="ml-2">
                          <Badge tone="red">po terminie</Badge>
                        </span>
                      )}
                    </td>
                    <td className="py-3 pr-4">
                      <Badge>{versionOf(c.schemaVersionId)}</Badge>
                    </td>
                    <td className="py-3 text-right">
                      <Link
                        href={`/cycles/${c.id}`}
                        className="rounded-md bg-navy-700 px-3 py-1.5 text-sm font-semibold text-white hover:bg-navy-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-600"
                      >
                        Otwórz
                      </Link>
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
