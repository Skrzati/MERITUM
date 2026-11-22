"use client";

import { useState } from "react";
import { Alert, Button, Card, Field, inputClass, ProblemAlert, Spinner, VisibilityBanner } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/labels";
import type { EmployeePrivateNote, PreInterviewSurvey } from "@/lib/types";
import { useAsync } from "@/lib/useAsync";

const MAX_LENGTH = 10_000;

/**
 * „Mój prywatny dziennik” (/api/private-notes) — widoczny wyłącznie dla właściciela, poza aktami, EZD i audytem treści.
 * Wpis można świadomie skopiować do odpowiedzi w ankiecie przed rozmową (gdy przełożony ją udostępnił).
 */
export function PrivateDiary({ cycleId }: { cycleId: number }) {
  const base = "/api/private-notes";
  const list = useAsync(() => api<EmployeePrivateNote[]>(`${base}?cycleId=${cycleId}`), [cycleId]);
  const survey = useAsync(() => api<PreInterviewSurvey>(`/api/cycles/${cycleId}/pre-interview-survey`).catch(() => null), [cycleId]);
  const [content, setContent] = useState("");
  const [editing, setEditing] = useState<{ id: number; content: string } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const surveyOpen = survey.data?.open && survey.data.status === "DRAFT" ? survey.data : null;

  const run = async (key: string, fn: () => Promise<unknown>, message?: string) => {
    setBusy(key);
    setError(null);
    setNotice(null);
    try {
      await fn();
      await list.reload();
      if (message) setNotice(message);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const copyToSurvey = (note: EmployeePrivateNote, questionId: string) =>
    run(
      `copy-${note.id}`,
      async () => {
        const current = surveyOpen?.questions.find((q) => q.id === questionId)?.answer?.trim();
        await api(`/api/cycles/${cycleId}/pre-interview-survey/answers`, {
          method: "PUT",
          body: { answers: { [questionId]: current ? `${current}\n${note.content}` : note.content } },
        });
        await survey.reload();
      },
      "Skopiowano wpis do ankiety przed rozmową (wersja robocza — przełożony zobaczy ją dopiero po wysłaniu).",
    );

  return (
    <Card title="Mój prywatny dziennik">
      <div className="space-y-4">
        <VisibilityBanner kind="private" />
        <Field label="Nowy wpis" hint="Np. osiągnięcia, trudności, tematy do poruszenia na rozmowie.">
          <textarea className={inputClass} rows={3} maxLength={MAX_LENGTH} value={content} onChange={(e) => setContent(e.target.value)} />
        </Field>
        <Button
          disabled={!content.trim()}
          busy={busy === "add"}
          onClick={() =>
            void run("add", async () => {
              await api(base, { body: { cycleId, content } });
              setContent("");
            })
          }
        >
          Zapisz wpis
        </Button>

        {list.loading && !list.data && <Spinner />}
        <ProblemAlert error={list.error ?? error} />
        {notice && !error && <Alert tone="green">{notice}</Alert>}

        {list.data && list.data.length === 0 && <p className="text-sm text-slate-700">Brak wpisów.</p>}
        {list.data && list.data.length > 0 && (
          <ul className="space-y-3">
            {list.data.map((n) => (
              <li key={n.id} className="rounded border border-slate-200 p-3 text-sm">
                <p className="text-xs text-slate-600">
                  {formatDate(n.createdAt)}
                  {n.updatedAt && n.updatedAt !== n.createdAt ? ` · zmieniono ${formatDate(n.updatedAt)}` : ""}
                </p>
                {editing?.id === n.id ? (
                  <div className="mt-2 space-y-2">
                    <textarea
                      aria-label="Edycja wpisu"
                      className={inputClass}
                      rows={3}
                      maxLength={MAX_LENGTH}
                      value={editing.content}
                      onChange={(e) => setEditing({ id: n.id, content: e.target.value })}
                    />
                    <div className="flex gap-2">
                      <Button
                        busy={busy === `save-${n.id}`}
                        disabled={!editing.content.trim()}
                        onClick={() =>
                          void run(`save-${n.id}`, async () => {
                            await api(`${base}/${n.id}`, { method: "PUT", body: { cycleId, content: editing.content } });
                            setEditing(null);
                          })
                        }
                      >
                        Zapisz
                      </Button>
                      <Button variant="secondary" onClick={() => setEditing(null)}>
                        Anuluj
                      </Button>
                    </div>
                  </div>
                ) : (
                  <p className="mt-1 whitespace-pre-wrap text-slate-900">{n.content}</p>
                )}
                {editing?.id !== n.id && (
                  <div className="mt-2 flex flex-wrap items-center gap-2">
                    <Button variant="secondary" onClick={() => setEditing({ id: n.id, content: n.content })}>
                      Edytuj
                    </Button>
                    <Button
                      variant="secondary"
                      busy={busy === `del-${n.id}`}
                      onClick={() => void run(`del-${n.id}`, () => api(`${base}/${n.id}`, { method: "DELETE" }), "Usunięto wpis.")}
                    >
                      Usuń
                    </Button>
                    {surveyOpen && <CopyToSurvey survey={surveyOpen} busy={busy === `copy-${n.id}`} onCopy={(qid) => void copyToSurvey(n, qid)} />}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
        {!surveyOpen && (
          <p className="text-xs text-slate-600">
            Kopiowanie wpisu do ankiety przed rozmową będzie możliwe, gdy przełożony udostępni ankietę.
          </p>
        )}
      </div>
    </Card>
  );
}

function CopyToSurvey({ survey, busy, onCopy }: { survey: PreInterviewSurvey; busy: boolean; onCopy: (questionId: string) => void }) {
  const [questionId, setQuestionId] = useState(survey.questions[0]?.id ?? "");
  if (!survey.questions.length) return null;
  return (
    <span className="flex flex-wrap items-center gap-2">
      <select aria-label="Pytanie w ankiecie przed rozmową" className={`${inputClass} w-auto`} value={questionId} onChange={(e) => setQuestionId(e.target.value)}>
        {survey.questions.map((q, i) => (
          <option key={q.id} value={q.id}>
            {i + 1}. {q.text.length > 60 ? `${q.text.slice(0, 60)}…` : q.text}
          </option>
        ))}
      </select>
      <Button variant="secondary" busy={busy} disabled={!questionId} onClick={() => onCopy(questionId)}>
        Skopiuj do ankiety przed rozmową
      </Button>
    </span>
  );
}
