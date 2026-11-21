"use client";

import { useState } from "react";
import { HistoryTable } from "@/components/hr/EmployeeHistoryPanel";
import { Alert, Button, Card, Field, inputClass, ProblemAlert, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate, STATUS_LABEL } from "@/lib/labels";
import type { Cycle, CycleStatus, HistoryEntry } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";
import type { CycleView } from "./context";

/** Dozwolone cofnięcia etapu w trybie korekty (lustro CycleStatus.CORRECTIONS w backendzie — decyduje backend). */
const CORRECTIONS: Partial<Record<CycleStatus, CycleStatus[]>> = {
  OBSERVATION: ["DRAFT_CRITERIA"],
  EVALUATION_DRAFT: ["OBSERVATION"],
  AWAITING_ACK: ["EVALUATION_DRAFT"],
  COMPLETED: ["AWAITING_ACK", "EVALUATION_DRAFT"],
};
const ACTIVE: CycleStatus[] = ["DRAFT_CRITERIA", "CRITERIA_CONFIRMED", "OBSERVATION", "EVALUATION_DRAFT", "AWAITING_ACK"];

/** Pola formularza (miejscowość, okres „od–do”), tryb korekty kadr/DG/admina i historia ocen ocenianego. */
export function FormCorrectionPanel({ view }: { view: CycleView }) {
  const { cycle, me, isEvaluator, isOfficeHr, isOfficeDg } = view;
  const canEditDetails = (isEvaluator || isOfficeHr) && ACTIVE.includes(cycle.status);
  const canCorrect = isOfficeHr || isOfficeDg || me.role === "KPRM_ADMIN";
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {canEditDetails && <DetailsCard view={view} />}
      {canCorrect && <CorrectionCard view={view} />}
      <div className="lg:col-span-2">
        <HistoryCard cycleId={cycle.id} />
      </div>
    </div>
  );
}

function DetailsCard({ view }: { view: CycleView }) {
  const { cycle, refresh } = view;
  const [place, setPlace] = useState(cycle.place ?? "");
  const [startDate, setStartDate] = useState(cycle.startDate);
  const [dueDate, setDueDate] = useState(cycle.dueDate);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);
  const periodChanged = startDate !== cycle.startDate || dueDate !== cycle.dueDate;
  const reasonRequired = periodChanged && cycle.status !== "DRAFT_CRITERIA";

  const save = async () => {
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      await api<Cycle>(`/api/cycles/${cycle.id}/details`, {
        method: "PATCH",
        body: { place, startDate, dueDate, reason: reason || null },
      });
      setSaved(true);
      setReason("");
      await refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Dane formularza (cz. I–II)">
      <div className="space-y-3">
        <Field label="Miejscowość">
          <input className={inputClass} value={place} onChange={(e) => setPlace(e.target.value)} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Okres oceny — od">
            <input type="date" className={inputClass} value={startDate} onChange={(e) => setStartDate(e.target.value)} />
          </Field>
          <Field label="Termin sporządzenia oceny — do">
            <input type="date" className={inputClass} value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
          </Field>
        </div>
        {reasonRequired && (
          <Field label="Uzasadnienie zmiany okresu (wymagane po potwierdzeniu kryteriów)">
            <textarea className={inputClass} rows={2} value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
        )}
        <p className="text-xs text-slate-600">
          Zmiana terminu z przesłanek art. 81 ust. 8 — w sekcji „Zmiana terminu oceny” (z zawiadomieniem § 8). Każda zmiana trafia do
          audytu.
        </p>
        <Button onClick={save} busy={busy} disabled={reasonRequired && !reason.trim()}>
          Zapisz dane formularza
        </Button>
        <ProblemAlert error={error} />
        {saved && !error && <Alert tone="green">Zapisano.</Alert>}
      </div>
    </Card>
  );
}

function CorrectionCard({ view }: { view: CycleView }) {
  const { cycle, refresh } = view;
  const targets = CORRECTIONS[cycle.status] ?? [];
  const [target, setTarget] = useState<CycleStatus | "">("");
  const [reason, setReason] = useState("");
  const [fields, setFields] = useState({
    criteriaInterviewDate: cycle.criteriaInterviewDate ?? "",
    evaluationInterviewDate: cycle.evaluationInterviewDate ?? "",
    acknowledgedAt: cycle.acknowledgedAt ?? "",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [done, setDone] = useState(false);

  const submit = async () => {
    setBusy(true);
    setError(null);
    setDone(false);
    const changed = (key: keyof typeof fields, original: string | null) => (fields[key] && fields[key] !== original ? fields[key] : null);
    try {
      await api<Cycle>(`/api/cycles/${cycle.id}/admin-correction`, {
        body: {
          targetStatus: target || null,
          reason,
          criteriaInterviewDate: changed("criteriaInterviewDate", cycle.criteriaInterviewDate),
          evaluationInterviewDate: changed("evaluationInterviewDate", cycle.evaluationInterviewDate),
          acknowledgedAt: changed("acknowledgedAt", cycle.acknowledgedAt),
        },
      });
      setDone(true);
      setReason("");
      setTarget("");
      await refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Tryb korekty (kadry / DG / admin KPRM)">
      <Alert tone="amber">
        Korekta pól zablokowanych po zakończeniu etapu — zawsze z uzasadnieniem, zapisywana w audycie jako <code>ADMIN_CORRECTION</code>.
      </Alert>
      <div className="mt-3 space-y-3">
        <Field label={`Cofnij etap (obecnie: ${STATUS_LABEL[cycle.status]})`}>
          <select className={inputClass} value={target} onChange={(e) => setTarget(e.target.value as CycleStatus | "")}>
            <option value="">— bez zmiany etapu —</option>
            {targets.map((t) => (
              <option key={t} value={t}>
                {STATUS_LABEL[t]}
              </option>
            ))}
          </select>
        </Field>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Data rozmowy rozpoczynającej">
            <input type="date" className={inputClass} value={fields.criteriaInterviewDate} onChange={(e) => setFields((f) => ({ ...f, criteriaInterviewDate: e.target.value }))} />
          </Field>
          <Field label="Data rozmowy oceniającej">
            <input type="date" className={inputClass} value={fields.evaluationInterviewDate} onChange={(e) => setFields((f) => ({ ...f, evaluationInterviewDate: e.target.value }))} />
          </Field>
          <Field label="Data zapoznania">
            <input type="date" className={inputClass} value={fields.acknowledgedAt} onChange={(e) => setFields((f) => ({ ...f, acknowledgedAt: e.target.value }))} />
          </Field>
        </div>
        <Field label="Uzasadnienie korekty (wymagane)">
          <textarea className={inputClass} rows={2} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Button variant="danger" onClick={submit} busy={busy} disabled={!reason.trim()}>
          Zastosuj korektę
        </Button>
        <ProblemAlert error={error} />
        {done && !error && <Alert tone="green">Korekta zapisana w audycie.</Alert>}
        <p className="text-xs text-slate-600">Termin oceny: {formatDate(cycle.dueDate)} · zapoznanie: {formatDate(cycle.acknowledgedAt)}</p>
      </div>
    </Card>
  );
}

function HistoryCard({ cycleId }: { cycleId: number }) {
  const history = useAsync(() => api<HistoryEntry[]>(`/api/cycles/${cycleId}/history`), [cycleId]);
  return (
    <Card title="Historia ocen ocenianego">
      {history.loading && !history.data && <Spinner />}
      <ProblemAlert error={history.error} />
      {history.data && <HistoryTable entries={history.data} />}
    </Card>
  );
}
