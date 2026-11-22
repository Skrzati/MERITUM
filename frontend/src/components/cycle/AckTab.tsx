"use client";

import { useState } from "react";
import { Alert, Button, Card, Field, inputClass, ProblemAlert, VisibilityBanner } from "@/components/ui";
import { api, download, saveBlob } from "@/lib/api";
import { formatDate, SIGNATURE_MODE_LABEL, signatureMode, today } from "@/lib/labels";
import type { Cycle, DgDecision, SignatureMode } from "@/lib/types";
import type { CycleView } from "./context";
import { ScorePicker } from "./ScorePicker";

const SIGNATURE_MODES = Object.keys(SIGNATURE_MODE_LABEL) as SignatureMode[];
const ACK_BUTTON: Record<SignatureMode, string> = {
  QUALIFIED_OR_TRUSTED: "Podpisz elektronicznie i potwierdź zapoznanie",
  INTERNAL_AUDIT_STAMP: "Potwierdź zapoznanie w systemie",
  HYBRID_PAPER_SCAN: "Potwierdź zapoznanie (podpis odręczny)",
};

export function AckTab({ view }: { view: CycleView }) {
  const { cycle, isEmployee, isEvaluator, isOfficeHr, isOfficeDg, refresh } = view;
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [refusalReason, setRefusalReason] = useState("");
  const [refusalDate, setRefusalDate] = useState(today());
  const [appealText, setAppealText] = useState("");
  const [decision, setDecision] = useState<DgDecision>("REDO");
  const [dgComment, setDgComment] = useState("");
  const [dgScores, setDgScores] = useState<Record<string, number>>(cycle.scores ?? {});
  const [dgJustification, setDgJustification] = useState(cycle.justification ?? "");
  const [signature, setSignature] = useState<SignatureMode>("QUALIFIED_OR_TRUSTED");
  const [paperSignedOn, setPaperSignedOn] = useState(today());
  const [scanReference, setScanReference] = useState("");

  const [courtFiledOn, setCourtFiledOn] = useState(today());
  const [courtCase, setCourtCase] = useState("");
  const [courtOutcome, setCourtOutcome] = useState("");

  const appealOpen = cycle.appealAllowed && (!cycle.appealDeadline || cycle.appealDeadline >= today());
  const officeManagement = isOfficeHr || isOfficeDg;

  const run = async (key: string, fn: () => Promise<unknown>) => {
    setBusy(key);
    setError(null);
    try {
      await fn();
      await refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const exportFile = async (key: string, fn: () => Promise<void>) => {
    setBusy(key);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };
  const pdf = () => exportFile("pdf", () => download(`/api/cycles/${cycle.id}/pdf`, `arkusz-oceny-${cycle.id}.pdf`));
  const pdfPartOne = () =>
    exportFile("pdf1", () => download(`/api/cycles/${cycle.id}/pdf?parts=1`, `arkusz-oceny-${cycle.id}-czesc-I.pdf`));
  const ezd = () =>
    exportFile("ezd", async () => {
      const pkg = await api<unknown>(`/api/cycles/${cycle.id}/ezd-package`);
      saveBlob(new Blob([JSON.stringify(pkg, null, 2)], { type: "application/json" }), `ezd-pakiet-cykl-${cycle.id}.json`);
    });

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card title="Stan zapoznania i sprzeciwu">
        <div className="mb-4">
          <VisibilityBanner kind="official" />
        </div>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <dt className="text-slate-600">Data zapoznania</dt>
          <dd>{formatDate(cycle.acknowledgedAt)}</dd>
          <dt className="text-slate-600">Metoda podpisu</dt>
          <dd>{cycle.signatureMethod ? SIGNATURE_MODE_LABEL[signatureMode(cycle.signatureMethod)].label : "—"}</dd>
          {cycle.paperSignedOn && (
            <>
              <dt className="text-slate-600">Data podpisu odręcznego</dt>
              <dd>
                {formatDate(cycle.paperSignedOn)}
                {cycle.scanReference ? ` · skan: ${cycle.scanReference}` : ""}
              </dd>
            </>
          )}
          {cycle.signatureVerificationCode && (
            <>
              <dt className="text-slate-600">Kod weryfikacyjny (drukowany na PDF)</dt>
              <dd className="font-mono font-bold tracking-wider">{cycle.signatureVerificationCode}</dd>
            </>
          )}
          {cycle.signatureHash && (
            <>
              <dt className="text-slate-600">Skrót SHA-256 treści arkusza{cycle.signedAt ? ` (${formatDate(cycle.signedAt)})` : ""}</dt>
              <dd className="break-all font-mono text-xs">{cycle.signatureHash}</dd>
            </>
          )}
          <dt className="text-slate-600">Termin na sprzeciw (7 dni od zapoznania, art. 83 ust. 1)</dt>
          <dd className="font-semibold">{cycle.appealDeadline ? formatDate(cycle.appealDeadline) : "wyliczany po zapoznaniu"}</dd>
          {cycle.delayReason && (
            <>
              <dt className="text-slate-600">Przyczyna opóźnienia oceny</dt>
              <dd>{cycle.delayReason}</dd>
            </>
          )}
          {cycle.refusalDate && (
            <>
              <dt className="text-slate-600">Odmowa zapoznania</dt>
              <dd>
                {formatDate(cycle.refusalDate)} — {cycle.refusalReason}
              </dd>
            </>
          )}
          {cycle.appealedAt && (
            <>
              <dt className="text-slate-600">Sprzeciw złożony</dt>
              <dd>{formatDate(cycle.appealedAt)}</dd>
              <dt className="text-slate-600">Termin decyzji — {cycle.appealAuthority} (14 dni, art. 83 ust. 3)</dt>
              <dd>
                {formatDate(cycle.appealDecisionDeadline)}
                {cycle.status === "APPEALED" && cycle.appealDecisionDeadline && cycle.appealDecisionDeadline < today() && (
                  <span className="ml-2 font-semibold text-red-800">termin minął</span>
                )}
              </dd>
            </>
          )}
          {cycle.dgDecision && (
            <>
              <dt className="text-slate-600">Decyzja ({cycle.appealAuthority})</dt>
              <dd>
                {cycle.dgDecision === "CHANGE" ? "zmiana oceny" : "ponowne sporządzenie"} ({formatDate(cycle.dgDecidedAt)})
                {cycle.dgComment ? ` — ${cycle.dgComment}` : ""}
              </dd>
            </>
          )}
        </dl>
        {cycle.appealText && <p className="mt-3 rounded bg-slate-50 p-2 text-sm ring-1 ring-inset ring-slate-200">„{cycle.appealText}”</p>}

        <div className="mt-5 border-t border-slate-200 pt-4">
          <div className="flex flex-wrap gap-2">
            <Button onClick={pdf} busy={busy === "pdf"}>
              ⬇ Pobierz arkusz PDF (1:1 ze wzorem)
            </Button>
            <Button variant="secondary" onClick={pdfPartOne} busy={busy === "pdf1"}>
              Drukuj wyłącznie Część I (PDF)
            </Button>
            <Button variant="secondary" onClick={ezd} busy={busy === "ezd"}>
              Metadane dla EZD RP (JSON)
            </Button>
            <Button
              variant="secondary"
              busy={busy === "zip"}
              onClick={() => exportFile("zip", () => download(`/api/cycles/${cycle.id}/ezd-zip`, `ezd-paczka-cykl-${cycle.id}.zip`))}
            >
              Pobierz paczkę EZD RP (.zip)
            </Button>
          </div>
          <p className="mt-1 text-xs text-slate-600">
            Arkusz wg wzoru z rozporządzenia (Gotenberg, PDF/A-2b) oraz metadane dokumentu do rejestracji w EZD RP (KRI).
          </p>
        </div>
        <div className="mt-4">
          <ProblemAlert error={error} />
        </div>
      </Card>

      <div className="space-y-6">
        {cycle.status === "AWAITING_ACK" && isEmployee && (
          <Card title="Zapoznanie z oceną">
            <p className="mb-3 text-sm text-slate-700">Po potwierdzeniu biegnie 7-dniowy termin na wniesienie sprzeciwu do organu: {cycle.appealAuthority.toLowerCase()}.</p>
            <fieldset className="mb-4 space-y-2">
              <legend className="mb-1 text-sm font-medium text-slate-800">Metoda podpisu</legend>
              {SIGNATURE_MODES.map((mode) => (
                <label key={mode} className="flex items-start gap-2 rounded-md border border-slate-200 p-2 text-sm hover:bg-slate-50">
                  <input
                    type="radio"
                    name="signature-method"
                    className="mt-0.5 h-4 w-4 accent-navy-700"
                    checked={signature === mode}
                    onChange={() => setSignature(mode)}
                  />
                  <span>
                    <span className="font-medium">{SIGNATURE_MODE_LABEL[mode].label}</span>
                    <span className="block text-xs text-slate-600">{SIGNATURE_MODE_LABEL[mode].hint}</span>
                  </span>
                </label>
              ))}
            </fieldset>
            {signature === "HYBRID_PAPER_SCAN" && (
              <div className="mb-3 space-y-3">
                <Button variant="secondary" onClick={pdf} busy={busy === "pdf"}>
                  🖨 Pobierz PDF do wydruku (z kodem weryfikacyjnym)
                </Button>
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field label="Data podpisu odręcznego (wymagana)">
                    <input
                      type="date"
                      required
                      max={today()}
                      className={inputClass}
                      value={paperSignedOn}
                      onChange={(e) => setPaperSignedOn(e.target.value)}
                    />
                  </Field>
                  <Field label="Numer skanu w EZD (opcjonalnie)">
                    <input className={inputClass} maxLength={255} value={scanReference} onChange={(e) => setScanReference(e.target.value)} />
                  </Field>
                </div>
              </div>
            )}
            <Button
              disabled={signature === "HYBRID_PAPER_SCAN" && !paperSignedOn}
              onClick={() =>
                run("ack", () =>
                  api<Cycle>(`/api/cycles/${cycle.id}/acknowledge`, {
                    body: {
                      signatureMethod: signature,
                      paperSignedOn: signature === "HYBRID_PAPER_SCAN" ? paperSignedOn : null,
                      scanReference: signature === "HYBRID_PAPER_SCAN" && scanReference.trim() ? scanReference.trim() : null,
                    },
                  }),
                )
              }
              busy={busy === "ack"}
            >
              {ACK_BUTTON[signature]}
            </Button>
            <p className="mt-2 text-xs text-slate-600">
              Odmowa podpisu nie wstrzymuje biegu terminów — przełożony lub kadry stwierdzają ją w systemie (fikcja zapoznania).
            </p>
          </Card>
        )}

        {cycle.status === "AWAITING_ACK" && (isEvaluator || isOfficeHr) && (
          <Card title="Odmowa potwierdzenia zapoznania (fikcja zapoznania)">
            <div className="space-y-3">
              <Field label="Powód / okoliczności">
                <textarea className={inputClass} rows={2} value={refusalReason} onChange={(e) => setRefusalReason(e.target.value)} />
              </Field>
              <Field label="Data odmowy">
                <input type="date" max={today()} className={inputClass} value={refusalDate} onChange={(e) => setRefusalDate(e.target.value)} />
              </Field>
              <Button
                variant="secondary"
                disabled={!refusalReason.trim()}
                busy={busy === "refusal"}
                onClick={() =>
                  run("refusal", () => api<Cycle>(`/api/cycles/${cycle.id}/refusal`, { body: { reason: refusalReason, date: refusalDate } }))
                }
              >
                Stwierdź odmowę potwierdzenia zapoznania
              </Button>
            </div>
          </Card>
        )}

        {isEmployee && appealOpen && (
          <Card title={cycle.appealRound > 0 ? "Ponowny sprzeciw od oceny sporządzonej po raz drugi" : "Sprzeciw od oceny"}>
            <div className="space-y-3">
              {cycle.appealRound > 0 && (
                <Alert tone="blue">Ocenę sporządzono ponownie po uwzględnieniu sprzeciwu — przysługuje ponowny sprzeciw (art. 83 ust. 4).</Alert>
              )}
              <Field label="Treść sprzeciwu" hint={`Do: ${cycle.appealAuthority.toLowerCase()} · termin: ${formatDate(cycle.appealDeadline)}`}>
                <textarea className={inputClass} rows={4} value={appealText} onChange={(e) => setAppealText(e.target.value)} />
              </Field>
              <Button
                variant="danger"
                disabled={!appealText.trim()}
                busy={busy === "appeal"}
                onClick={() => run("appeal", () => api<Cycle>(`/api/cycles/${cycle.id}/appeal`, { body: { content: appealText } }))}
              >
                Złóż sprzeciw
              </Button>
            </div>
          </Card>
        )}

        {cycle.status === "APPEALED" && isOfficeDg && (
          <Card title={`Rozpatrzenie sprzeciwu — ${cycle.appealAuthority}`}>
            <div className="space-y-3">
              <fieldset className="flex gap-4 text-sm">
                <legend className="sr-only">Rodzaj decyzji</legend>
                {(["REDO", "CHANGE"] as DgDecision[]).map((d) => (
                  <label key={d} className="flex items-center gap-2">
                    <input type="radio" name="dg-decision" className="h-4 w-4 accent-navy-700" checked={decision === d} onChange={() => setDecision(d)} />
                    {d === "REDO" ? "Ponowne sporządzenie oceny" : "Zmiana oceny"}
                  </label>
                ))}
              </fieldset>
              {decision === "CHANGE" && cycle.criteria && (
                <>
                  <ScorePicker criteria={cycle.criteria} scores={dgScores} onChange={(code, v) => setDgScores((s) => ({ ...s, [code]: v }))} />
                  <Field label="Uzasadnienie zmienionej oceny">
                    <textarea className={inputClass} rows={3} value={dgJustification} onChange={(e) => setDgJustification(e.target.value)} />
                  </Field>
                </>
              )}
              <Field label={`Komentarz (${cycle.appealAuthority.toLowerCase()})`}>
                <textarea className={inputClass} rows={2} value={dgComment} onChange={(e) => setDgComment(e.target.value)} />
              </Field>
              <Button
                busy={busy === "dg"}
                onClick={() =>
                  run("dg", () =>
                    api<Cycle>(`/api/cycles/${cycle.id}/appeal/decision`, {
                      body: {
                        decision,
                        comment: dgComment,
                        scores: decision === "CHANGE" ? dgScores : null,
                        justification: decision === "CHANGE" ? dgJustification : null,
                      },
                    }),
                  )
                }
              >
                Zatwierdź rozstrzygnięcie
              </Button>
            </div>
          </Card>
        )}

        {cycle.status === "COMPLETED" && !cycle.appealAllowed && (
          <Alert tone="slate">Ocena zmieniona w trybie sprzeciwu — przysługuje odwołanie do sądu pracy (art. 83 ust. 5).</Alert>
        )}
        {cycle.status === "COMPLETED" && cycle.appealAllowed && !appealOpen && (
          <Alert tone="slate">Termin na sprzeciw upłynął — ocena jest ostateczna.</Alert>
        )}

        {cycle.legalHold && (
          <Alert tone="red" title="Blokada archiwalna — spór przed sądem pracy">
            Odwołanie wniesione {formatDate(cycle.courtAppealFiledOn)}
            {cycle.courtCaseReference ? ` (sygn. ${cycle.courtCaseReference})` : ""}. Arkusz i dokumentacja cyklu nie podlegają czyszczeniu ani
            brakowaniu do prawomocnego zakończenia sporu.
          </Alert>
        )}

        {officeManagement && cycle.status === "COMPLETED" && (
          <Card title="Odwołanie do sądu pracy (art. 83 ust. 5)">
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Data wniesienia odwołania">
                <input type="date" className={inputClass} max={today()} value={courtFiledOn} onChange={(e) => setCourtFiledOn(e.target.value)} />
              </Field>
              <Field label="Sygnatura akt (opcjonalnie)">
                <input className={inputClass} value={courtCase} maxLength={100} onChange={(e) => setCourtCase(e.target.value)} />
              </Field>
            </div>
            <Button
              variant="danger"
              busy={busy === "court"}
              onClick={() =>
                run("court", () =>
                  api<Cycle>(`/api/cycles/${cycle.id}/court-appeal`, { body: { filedOn: courtFiledOn, caseReference: courtCase || null } }),
                )
              }
            >
              Zarejestruj odwołanie (blokada archiwalna)
            </Button>
          </Card>
        )}

        {officeManagement && cycle.status === "APPEALED_TO_COURT" && (
          <Card title="Zakończenie sporu sądowego">
            <Field label="Rozstrzygnięcie (sygnatura, treść wyroku)">
              <textarea className={inputClass} rows={2} value={courtOutcome} onChange={(e) => setCourtOutcome(e.target.value)} />
            </Field>
            <Button
              disabled={!courtOutcome.trim()}
              busy={busy === "court-close"}
              onClick={() => run("court-close", () => api<Cycle>(`/api/cycles/${cycle.id}/court-appeal/close`, { body: { outcome: courtOutcome } }))}
            >
              Zarejestruj prawomocne zakończenie (zdejmij blokadę archiwalną)
            </Button>
          </Card>
        )}
      </div>
    </div>
  );
}
