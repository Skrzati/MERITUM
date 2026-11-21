"use client";

import { useState } from "react";
import { ApiError } from "@/lib/api";
import type { AiMode, Problem } from "@/lib/types";
import type { Tone } from "@/lib/labels";

const TONES: Record<Tone, string> = {
  slate: "bg-slate-100 text-slate-800 ring-slate-300",
  blue: "bg-blue-50 text-blue-900 ring-blue-300",
  amber: "bg-amber-50 text-amber-900 ring-amber-300",
  green: "bg-green-50 text-green-900 ring-green-300",
  red: "bg-red-50 text-red-900 ring-red-300",
};

export function Badge({ tone = "slate", children }: { tone?: Tone; children: React.ReactNode }) {
  return (
    <span className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold ring-1 ring-inset ${TONES[tone]}`}>
      {children}
    </span>
  );
}

export function Card({ title, actions, children }: { title?: string; actions?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      {(title || actions) && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          {title && <h2 className="text-lg font-semibold text-navy-900">{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

type ButtonVariant = "primary" | "secondary" | "danger" | "ai";
const BUTTONS: Record<ButtonVariant, string> = {
  primary: "bg-navy-700 text-white hover:bg-navy-800",
  secondary: "bg-white text-navy-800 ring-1 ring-inset ring-slate-300 hover:bg-slate-50",
  danger: "bg-red-700 text-white hover:bg-red-800",
  ai: "bg-indigo-700 text-white hover:bg-indigo-800",
};

export function Button({
  variant = "primary",
  busy = false,
  className = "",
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; busy?: boolean }) {
  return (
    <button
      {...props}
      disabled={props.disabled || busy}
      className={`inline-flex items-center justify-center gap-2 rounded-md px-3.5 py-2 text-sm font-semibold
        focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-navy-600
        disabled:cursor-not-allowed disabled:opacity-50 ${BUTTONS[variant]} ${className}`}
    >
      {busy && <span aria-hidden className="h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent" />}
      {children}
    </button>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-medium text-slate-800">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-slate-600">{hint}</span>}
    </label>
  );
}

export const inputClass =
  "block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm " +
  "focus:border-navy-600 focus:outline-none focus:ring-2 focus:ring-navy-600/30 disabled:bg-slate-100";

export function Alert({ tone = "blue", title, children }: { tone?: Tone; title?: string; children?: React.ReactNode }) {
  return (
    <div role={tone === "red" ? "alert" : "status"} className={`rounded-md p-3 text-sm ring-1 ring-inset ${TONES[tone]}`}>
      {title && <p className="font-semibold">{title}</p>}
      {children && <div className={title ? "mt-1" : ""}>{children}</div>}
    </div>
  );
}

/** Błąd API w formacie RFC 7807 — dla 422 pokazuje listę naruszeń reguł i wyliczony wynik. */
export function ProblemAlert({ error }: { error: unknown }) {
  if (!error) return null;
  const problem: Problem = error instanceof ApiError ? error.problem : { detail: String(error) };
  const status = error instanceof ApiError ? error.status : undefined;
  const title =
    status === 422 ? "Arkusz narusza reguły oceny okresowej" : status === 403 ? "Brak uprawnień" : status === 409 ? "Operacja niedozwolona w tym etapie" : "Błąd";
  return (
    <Alert tone="red" title={title}>
      {problem.result && <p className="font-medium">{problem.result.explanation}</p>}
      {problem.violations?.length ? (
        <ul className="mt-1 list-disc pl-5">
          {problem.violations.map((v) => (
            <li key={v}>{v}</li>
          ))}
        </ul>
      ) : (
        status !== 422 && <p>{problem.detail ?? problem.title}</p>
      )}
    </Alert>
  );
}

export function ModeBadge({ mode }: { mode: AiMode }) {
  const tone: Tone = mode === "LIVE" ? "green" : "amber";
  const label = mode === "LIVE" ? "LLM: LIVE" : mode === "MOCK" ? "LLM: MOCK (offline)" : "LLM: MOCK (fallback)";
  return <Badge tone={tone}>{label}</Badge>;
}

export function AiDisclaimer({ text }: { text: string }) {
  return (
    <p className="flex items-start gap-2 rounded bg-indigo-50 px-3 py-2 text-xs font-medium text-indigo-900 ring-1 ring-inset ring-indigo-200">
      <span aria-hidden>🤖</span>
      {text}
    </p>
  );
}

/** Podgląd tekstu wysłanego do LLM — maskowanie PII „na żywo”. */
export function MaskedPrompt({ prompt }: { prompt: string }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="rounded-md border border-dashed border-slate-300">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center justify-between px-3 py-2 text-left text-sm font-medium text-navy-800 hover:bg-slate-50"
      >
        <span>🔒 Co widzi model AI? (prompt po maskowaniu PII)</span>
        <span aria-hidden>{open ? "▲" : "▼"}</span>
      </button>
      {open && (
        <pre className="max-h-72 overflow-auto whitespace-pre-wrap border-t border-slate-200 bg-slate-50 px-3 py-2 font-mono text-xs text-slate-800">
          {highlightTokens(prompt)}
        </pre>
      )}
    </div>
  );
}

function highlightTokens(text: string): React.ReactNode[] {
  return text.split(/(\[[A-Z]+_\d+\])/g).map((part, i) =>
    /^\[[A-Z]+_\d+\]$/.test(part) ? (
      <mark key={i} className="rounded bg-amber-200 px-0.5 text-amber-950">
        {part}
      </mark>
    ) : (
      part
    ),
  );
}

export function Spinner({ label = "Ładowanie…" }: { label?: string }) {
  return (
    <p className="flex items-center gap-2 text-sm text-slate-600" role="status">
      <span aria-hidden className="h-4 w-4 animate-spin rounded-full border-2 border-navy-600 border-t-transparent" />
      {label}
    </p>
  );
}

export function Stat({ label, value, hint, tone = "slate" }: { label: string; value: React.ReactNode; hint?: string; tone?: Tone }) {
  return (
    <div className={`rounded-lg p-4 ring-1 ring-inset ${TONES[tone]}`}>
      <p className="text-xs font-semibold uppercase tracking-wide opacity-80">{label}</p>
      <p className="mt-1 text-3xl font-bold">{value}</p>
      {hint && <p className="mt-1 text-xs opacity-80">{hint}</p>}
    </div>
  );
}
