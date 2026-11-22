"use client";

import { RoleGate } from "@/components/RoleGate";
import { CalibrationCard } from "@/components/stats/CalibrationCard";
import { MentoringCard } from "@/components/stats/MentoringCard";
import { Alert, Card, ProblemAlert, Spinner, Stat } from "@/components/ui";
import { useUser } from "@/components/UserProvider";
import { api } from "@/lib/api";
import { LEVEL_LABEL, OUTCOME_LABEL, STATUS_LABEL } from "@/lib/labels";
import type { AnonymizedCount, StatsSummary, WellbeingSummary } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

/** Wyświetla liczność po anonimizacji serwerowej — UI nie zna surowych wartości 1..k-1. */
function show(c: AnonymizedCount, k: number): string {
  return c.suppressed ? `<${k}` : String(c.value ?? 0);
}

export default function StatsPage() {
  return (
    <RoleGate roles={["DG", "HR"]}>
      <Stats />
    </RoleGate>
  );
}

function Stats() {
  const { current } = useUser();
  const stats = useAsync(() => api<StatsSummary>("/api/stats/summary"), [current?.id]);
  const s = stats.data;

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gov-text">Statystyki urzędu</h1>
      {stats.loading && <Spinner />}
      <ProblemAlert error={stats.error} />
      {s && (
        <>
          <Alert tone="blue" title={`Anonimizacja po stronie serwera (k = ${s.kAnonymity})`}>
            Backend zwraca wyłącznie agregaty. Liczności od 1 do {s.kAnonymity - 1} nie opuszczają serwera — są oznaczone jako
            „&lt;{s.kAnonymity}”, aby nie dało się zidentyfikować pojedynczych osób. Zero jest podawane wprost.
          </Alert>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            <Stat label="Cykle ogółem" value={show(s.totalCycles, s.kAnonymity)} />
            <Stat
              label="Zaległe"
              value={show(s.overdue, s.kAnonymity)}
              tone={s.overdue.suppressed || (s.overdue.value ?? 0) > 0 ? "red" : "slate"}
              hint="po terminie, bez sporządzonej oceny"
            />
            <Stat label="Termin ≤ 7 dni" value={show(s.dueIn7Days, s.kAnonymity)} tone="amber" />
            <Stat label="Termin ≤ 30 dni" value={show(s.dueIn30Days, s.kAnonymity)} tone="amber" />
            <Stat label="Sprzeciwy" value={show(s.appeals, s.kAnonymity)} tone="blue" />
          </div>
          <div className="grid gap-6 lg:grid-cols-3">
            <Distribution title="Rozkład ocen" data={s.byOutcome} k={s.kAnonymity} label={(key) => OUTCOME_LABEL[key as keyof typeof OUTCOME_LABEL] ?? key} />
            <Distribution title="Poziomy wykonywania obowiązków" data={s.byLevel} k={s.kAnonymity} label={(key) => LEVEL_LABEL[key as keyof typeof LEVEL_LABEL] ?? key} />
            <Distribution title="Etapy procesu" data={s.byStatus} k={s.kAnonymity} label={(key) => STATUS_LABEL[key as keyof typeof STATUS_LABEL] ?? key} />
          </div>
          <WellbeingCard w={s.wellbeing} k={s.kAnonymity} />
          <CalibrationCard reloadKey={current?.id} />
          <MentoringCard />
        </>
      )}
    </div>
  );
}

function Distribution({
  title,
  data,
  k,
  label,
}: {
  title: string;
  data: Record<string, AnonymizedCount>;
  k: number;
  label: (key: string) => string;
}) {
  const entries = Object.entries(data);
  const total = entries.reduce((a, [, c]) => a + (c.value ?? 0), 0);
  return (
    <Card title={title}>
      {entries.length === 0 ? (
        <p className="text-sm text-slate-700">Brak danych.</p>
      ) : (
        <ul className="space-y-3">
          {entries.map(([key, c]) => (
            <li key={key}>
              <div className="flex justify-between text-sm">
                <span>{label(key)}</span>
                <strong>{show(c, k)}</strong>
              </div>
              <div className="mt-1 h-2 rounded bg-slate-200" aria-hidden>
                {!c.suppressed && total > 0 && (
                  <div className="h-2 rounded bg-gov-blue" style={{ width: `${((c.value ?? 0) / total) * 100}%` }} />
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      <p className="mt-3 text-xs text-slate-600">Paski proporcji pomijają kategorie ukryte (&lt;{k}).</p>
    </Card>
  );
}

const WELLBEING_SCALE: { key: keyof Pick<WellbeingSummary, "avgWorkload" | "avgEnergy" | "avgSupport">; label: string; hint: string }[] = [
  { key: "avgWorkload", label: "Obciążenie", hint: "1 — niskie · 5 — bardzo wysokie" },
  { key: "avgEnergy", label: "Energia", hint: "1 — niska · 5 — wysoka" },
  { key: "avgSupport", label: "Wsparcie przełożonego", hint: "1 — niskie · 5 — wysokie" },
];

function WellbeingCard({ w, k }: { w: WellbeingSummary; k: number }) {
  const reportable = w.avgWorkload !== null;
  return (
    <Card title="Dobrostan zespołu (ankiety dobrowolne)">
      <p className="mb-4 text-sm text-slate-700">
        Ankiety są anonimowe, niepowiązane z oceną okresową i nieaudytowane. Średnie podajemy dopiero od {k} odpowiedzi.
      </p>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <Stat label="Odpowiedzi" value={show(w.responses, k)} />
        {WELLBEING_SCALE.map((m) => (
          <Stat
            key={m.key}
            label={m.label}
            value={w[m.key] !== null ? `${String(w[m.key]).replace(".", ",")} / 5` : "—"}
            hint={m.hint}
            tone="blue"
          />
        ))}
        <Stat label="Prośby o rozmowę" value={show(w.wantsConversation, k)} tone={w.wantsConversation.suppressed || (w.wantsConversation.value ?? 0) > 0 ? "amber" : "slate"} />
      </div>
      {!reportable && (
        <div className="mt-4">
          <Alert tone="slate">Za mało odpowiedzi (mniej niż {k}), aby bezpiecznie pokazać średnie.</Alert>
        </div>
      )}
    </Card>
  );
}
