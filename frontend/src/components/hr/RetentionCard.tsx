"use client";

import { useState } from "react";
import { Alert, Button, Card, ProblemAlert } from "@/components/ui";
import { api } from "@/lib/api";
import type { PurgeResult } from "@/lib/types";

/**
 * Retencja: arkusze ocen to część B akt osobowych (10 / 50 lat, legal hold przy sporze) — nie są usuwane.
 * Czyszczone są wyłącznie dane pomocnicze zamkniętych cykli (notatki robocze, ankiety) po okresie karencji.
 */
export function RetentionCard() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [result, setResult] = useState<PurgeResult | null>(null);

  const purge = async () => {
    setBusy(true);
    setError(null);
    try {
      setResult(await api<PurgeResult>("/api/hr/retention/purge-working-notes", { method: "POST" }));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title="Retencja i dane pomocnicze (RODO)">
      <div className="space-y-3 text-sm text-slate-700">
        <p>
          Arkusze ocen okresowych są przechowywane w części B akt osobowych — 10 lat (stosunek pracy od 1.01.2019) albo 50 lat — i nie
          podlegają automatycznemu usunięciu; spór przed sądem pracy ustawia blokadę (legal hold). Paczka EZD jawnie wyłącza notatki
          robocze i ankiety.
        </p>
        <p>
          Czyszczenie usuwa wyłącznie dane pomocnicze zamkniętych cykli (po 30 dniach od zamknięcia, bez legal hold). Uruchamia się też
          automatycznie w nocy.
        </p>
        <Button variant="secondary" busy={busy} onClick={() => void purge()}>
          Wyczyść dane pomocnicze zamkniętych cykli
        </Button>
        <ProblemAlert error={error} />
        {result && (
          <Alert tone="green">
            Wyczyszczono cykli: {result.cyclesPurged} (notatki: {result.notesDeleted}, ankiety: {result.surveysDeleted}). Pominięto z
            powodu legal hold: {result.skippedLegalHold}.
          </Alert>
        )}
      </div>
    </Card>
  );
}
