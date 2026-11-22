"use client";

import { useState } from "react";
import { ApiError } from "@/lib/api";
import type { AiMode, ComplianceMeta, Problem } from "@/lib/types";
import type { Tone } from "@/lib/labels";

const TONES: Record<Tone, string> = {
  slate: "bg-slate-100 text-slate-800 ring-slate-300",
  blue: "bg-gov-light text-gov-hover ring-navy-200",
  amber: "bg-amber-50 text-amber-900 ring-amber-300",
  green: "bg-green-50 text-green-900 ring-green-300",
  red: "bg-red-50 text-red-900 ring-red-300",
};

export function Badge({ tone = "slate", children }: { tone?: Tone; children: React.ReactNode }) {
  return (
    <span data-tone={tone} className={`a11y-status inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold ring-1 ring-inset ${TONES[tone]}`}>
      {children}
    </span>
  );
}

export type StatusKind = "ok" | "warn" | "err" | "info" | "neutral";

/** Odpowiednik tonu z `labels.ts` — ułatwia zamianę `Badge` na `StatusBadge`. */
export const TONE_STATUS: Record<Tone, StatusKind> = { green: "ok", amber: "warn", red: "err", blue: "info", slate: "neutral" };

const STATUS_STYLE: Record<StatusKind, { className: string; defaultLabel: string; icon: React.ReactNode }> = {
  ok: {
    className: "border-2 border-solid border-status-ok bg-status-ok-soft text-status-ok",
    defaultLabel: "W porządku",
    icon: <path d="M4 12.5l5 5L20 6.5" />,
  },
  warn: {
    className: "border-2 border-dashed border-status-warn bg-status-warn-soft text-status-warn",
    defaultLabel: "Uwaga",
    icon: <path d="M12 3L2 21h20zM12 10v5M12 18v.5" />,
  },
  err: {
    className: "border-4 border-double border-status-err bg-status-err-soft text-status-err",
    defaultLabel: "Błąd",
    icon: <path d="M12 2.5a9.5 9.5 0 1 0 0 19 9.5 9.5 0 0 0 0-19zM8.5 8.5l7 7M15.5 8.5l-7 7" />,
  },
  info: {
    className: "border-2 border-dotted border-status-info bg-status-info-soft text-status-info",
    defaultLabel: "Informacja",
    icon: <path d="M12 2.5a9.5 9.5 0 1 0 0 19 9.5 9.5 0 0 0 0-19zM12 11v6M12 7v.5" />,
  },
  neutral: {
    className: "border border-solid border-ui-muted bg-ui-surface-2 text-ui-text",
    defaultLabel: "Brak statusu",
    icon: <circle cx="12" cy="12" r="4" fill="currentColor" />,
  },
};

/**
 * Status, który nigdy nie polega wyłącznie na kolorze (WCAG 1.4.1): unikalna ikona + wzór obramowania
 * (ciągłe / przerywane / podwójne / kropkowane) + polska etykieta tekstowa.
 */
export function StatusBadge({ status, children }: { status: StatusKind; children?: React.ReactNode }) {
  const style = STATUS_STYLE[status];
  return (
    <span className={`inline-flex items-center gap-1.5 rounded px-2 py-0.5 text-xs font-semibold ${style.className}`}>
      <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" className="h-3.5 w-3.5 shrink-0" fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round">
        {style.icon}
      </svg>
      <span>{children ?? style.defaultLabel}</span>
    </span>
  );
}

export function Card({ title, actions, children }: { title?: string; actions?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className="rounded border border-slate-300 bg-white p-6 shadow-card">
      {(title || actions) && (
        <div className="mb-5 flex flex-wrap items-center justify-between gap-2">
          {title && <h2 className="border-l-4 border-gov-blue pl-3 text-xl font-bold text-gov-text">{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

type ButtonVariant = "primary" | "secondary" | "danger" | "ai";
const BUTTONS: Record<ButtonVariant, string> = {
  primary: "bg-gov-blue text-white shadow-sm hover:bg-gov-hover",
  secondary: "bg-white text-gov-blue ring-1 ring-inset ring-gov-blue hover:bg-gov-light",
  danger: "bg-red-700 text-white hover:bg-red-800",
  // Asystent AI: niebieski kontur gov.pl zamiast osobnego koloru — AI jest narzędziem pomocniczym, nie decyzją.
  ai: "bg-white text-gov-blue ring-2 ring-inset ring-gov-blue hover:bg-gov-light",
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
      className={`inline-flex min-h-[44px] items-center justify-center gap-2 rounded px-5 py-2 text-base font-bold
        focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-gov-blue
        disabled:cursor-not-allowed disabled:opacity-50 ${BUTTONS[variant]} ${className}`}
    >
      {busy && <span aria-hidden className="h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent" />}
      {variant === "ai" && !busy && <span aria-hidden className="text-xs">✦</span>}
      {children}
    </button>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <label className="block text-base">
      <span className="mb-1.5 block font-semibold text-gov-text">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-sm text-slate-600">{hint}</span>}
    </label>
  );
}

export const inputClass =
  "block min-h-[44px] w-full rounded border border-slate-400 bg-white px-3 py-2 text-base text-gov-text " +
  "focus:border-gov-blue focus:outline-none focus:ring-2 focus:ring-gov-blue/30 disabled:bg-slate-100";

export function Alert({ tone = "blue", title, children }: { tone?: Tone; title?: string; children?: React.ReactNode }) {
  return (
    <div role={tone === "red" ? "alert" : "status"} data-tone={tone} className={`a11y-status rounded p-3 text-sm ring-1 ring-inset ${TONES[tone]}`}>
      {title && <p className="font-semibold">{title}</p>}
      {children && <div className={title ? "mt-1" : ""}>{children}</div>}
    </div>
  );
}

/** Zwijana sekcja w stylu gov.pl (natywne details/summary) — zaawansowane opcje ukryte domyślnie. */
export function CollapsibleSection({
  title,
  hint,
  defaultOpen = false,
  children,
}: {
  title: string;
  hint?: string;
  defaultOpen?: boolean;
  children: React.ReactNode;
}) {
  return (
    <details open={defaultOpen} className="gov-collapsible rounded border border-slate-300 bg-white">
      <summary className="flex min-h-[44px] cursor-pointer items-center justify-between gap-3 px-4 py-3 text-base font-bold text-gov-blue hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-gov-blue">
        <span>
          {title}
          {hint && <span className="mt-0.5 block text-sm font-normal text-slate-600">{hint}</span>}
        </span>
        <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" className="gov-collapsible-arrow h-5 w-5 shrink-0 transition-transform" fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </summary>
      <div className="border-t border-slate-300 px-4 py-4">{children}</div>
    </details>
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
  const tone: Tone = mode === "LIVE" || mode === "RULES" ? "green" : "amber";
  const label =
    mode === "LIVE"
      ? "Model AI: aktywny"
      : mode === "RULES"
        ? "Reguły deterministyczne (bez modelu AI)"
        : mode === "MOCK"
          ? "Model AI: tryb demonstracyjny (bez sieci)"
          : "Model AI: tryb zastępczy (model niedostępny)";
  return <Badge tone={tone}>{label}</Badge>;
}

export function AiDisclaimer({ text }: { text: string }) {
  return (
    <p className="flex items-start gap-2 border-l-4 border-gov-blue bg-gov-light px-3 py-2 text-xs font-medium text-gov-hover">
      <span aria-hidden className="font-bold">AI</span>
      {text}
    </p>
  );
}

/** Podgląd tekstu wysłanego do LLM — maskowanie PII „na żywo”. */
export function MaskedPrompt({ prompt }: { prompt: string }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="rounded border border-dashed border-slate-400">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center justify-between px-3 py-2 text-left text-sm font-semibold text-gov-blue hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue"
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
      <span aria-hidden className="h-4 w-4 animate-spin rounded-full border-2 border-gov-blue border-t-transparent" />
      {label}
    </p>
  );
}

export function Stat({ label, value, hint, tone = "slate" }: { label: string; value: React.ReactNode; hint?: string; tone?: Tone }) {
  return (
    <div className={`rounded p-4 ring-1 ring-inset ${TONES[tone]}`}>
      <p className="text-xs font-semibold uppercase tracking-wide opacity-80">{label}</p>
      <p className="mt-1 text-3xl font-bold">{value}</p>
      {hint && <p className="mt-1 text-xs opacity-80">{hint}</p>}
    </div>
  );
}

export type Visibility = "private" | "shared-on-send" | "anonymous" | "official";

const VISIBILITY: Record<Visibility, { text: string; icon: React.ReactNode }> = {
  private: {
    text: "Prywatne — widoczne wyłącznie dla Ciebie (nie trafia do akt)",
    icon: <path d="M6 11V8a6 6 0 1 1 12 0v3M5 11h14v10H5z" />,
  },
  "shared-on-send": {
    text: "Udostępniane przełożonemu po kliknięciu „Wyślij” — nie trafia do akt osobowych",
    icon: <path d="M3 11l18-8-8 18-2-8z" />,
  },
  anonymous: {
    text: "Anonimowe — wyniki zbiorcze widoczne dla HR tylko w grupach min. 5 osób",
    icon: <path d="M8 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM16 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM2 20c0-3 3-5 6-5s6 2 6 5M14 15c3 0 8 1 8 5" />,
  },
  official: {
    text: "Dokument urzędowy — część B akt osobowych (retencja 10/50 lat)",
    icon: <path d="M6 2h9l5 5v15H6zM14 2v6h6M9 13h8M9 17h8" />,
  },
};

/** Kto zobaczy wpisywane dane — stały baner zaufania nad formularzem. */
export function VisibilityBanner({ kind }: { kind: Visibility }) {
  const v = VISIBILITY[kind];
  return (
    <p className="flex items-start gap-2 rounded border border-slate-300 bg-slate-50 px-3 py-2 text-sm font-semibold text-gov-text">
      <svg aria-hidden="true" focusable="false" viewBox="0 0 24 24" className="mt-0.5 h-4 w-4 shrink-0 text-gov-blue" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        {v.icon}
      </svg>
      <span>{v.text}</span>
    </p>
  );
}

/** Metadane zgodności z AI Act z odpowiedzi /api/ai/* — przy każdym wyniku asystenta. */
export function AiComplianceBadge({ meta }: { meta: ComplianceMeta | undefined }) {
  if (!meta) return null;
  const count = meta.redactedCount;
  return (
    <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs font-semibold text-gov-hover" title={meta.disclaimer}>
      <StatusBadge status={meta.piiAnonymized ? "ok" : "warn"}>
        {meta.piiAnonymized ? `Dane osobowe zanonimizowane (${count} ${count === 1 ? "pole" : count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 12 || count % 100 > 14) ? "pola" : "pól"})` : "Dane osobowe niezanonimizowane"}
      </StatusBadge>
      <span aria-hidden>•</span>
      <span>Szkic asystenta (AI Act: ostateczną decyzję podejmuje przełożony)</span>
    </p>
  );
}
