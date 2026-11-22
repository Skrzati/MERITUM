"use client";

import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import {
  A11Y_TOGGLES,
  COLOR_BLIND_LABEL,
  COLOR_BLIND_MODES,
  isHighContrast,
  THEME_LABEL,
  THEMES,
  TOGGLE_LABEL,
  ZOOM_LEVELS,
} from "@/lib/a11y";
import { useAccessibility } from "./AccessibilityProvider";

const CONTENT_WRAPPER_ID = "app-content-wrapper";
const PANEL_ID = "a11y-panel";
const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

const quickButton =
  "inline-flex h-9 min-w-9 items-center justify-center gap-1 rounded px-2 text-sm font-bold text-gov-blue ring-1 ring-inset ring-slate-400 " +
  "hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue aria-pressed:bg-gov-blue aria-pressed:text-white " +
  "disabled:cursor-not-allowed disabled:opacity-50";

/** Szybkie przełączniki w nagłówku + panel „Ułatwienia dostępu” (Alt+A). */
export function AccessibilityBar() {
  const { settings, setTheme, zoomIn, zoomOut, setZoom, panelOpen, openPanel } = useAccessibility();
  const highContrast = isHighContrast(settings.theme);
  const minZoom = settings.zoom === ZOOM_LEVELS[0];
  const maxZoom = settings.zoom === ZOOM_LEVELS[ZOOM_LEVELS.length - 1];

  return (
    <div role="group" aria-label="Ułatwienia dostępu — szybkie ustawienia" className="flex flex-wrap items-center gap-1.5">
      <button
        type="button"
        className={quickButton}
        aria-pressed={settings.theme === "dark"}
        aria-label="Tryb ciemny"
        onClick={() => setTheme(settings.theme === "dark" ? "light" : "dark")}
      >
        {settings.theme === "dark" ? <SunIcon /> : <MoonIcon />}
        <span className="hidden sm:inline">{settings.theme === "dark" ? "Jasny" : "Ciemny"}</span>
      </button>

      <div role="group" aria-label={`Wielkość tekstu: ${settings.zoom}%`} className="flex gap-0.5">
        <button type="button" className={quickButton} aria-label="Pomniejsz tekst" disabled={minZoom} onClick={zoomOut}>
          <span aria-hidden className="text-xs">A−</span>
        </button>
        <button
          type="button"
          className={quickButton}
          aria-label="Domyślna wielkość tekstu (100%)"
          aria-pressed={settings.zoom === 100}
          onClick={() => setZoom(100)}
        >
          <span aria-hidden>A</span>
        </button>
        <button type="button" className={quickButton} aria-label="Powiększ tekst" disabled={maxZoom} onClick={zoomIn}>
          <span aria-hidden className="text-base">A+</span>
        </button>
      </div>

      <button
        type="button"
        className={quickButton}
        aria-pressed={highContrast}
        aria-label="Wysoki kontrast"
        onClick={() => setTheme(highContrast ? "light" : "hc-yellow")}
      >
        <ContrastIcon />
        <span className="hidden sm:inline">Kontrast</span>
      </button>

      <button
        type="button"
        className={`${quickButton} px-3`}
        aria-haspopup="dialog"
        aria-expanded={panelOpen}
        aria-controls={panelOpen ? PANEL_ID : undefined}
        aria-keyshortcuts="Alt+A"
        onClick={openPanel}
      >
        <AccessibilityIcon />
        <span>Ułatwienia dostępu</span>
      </button>

      {panelOpen && <AccessibilityPanel />}
    </div>
  );
}

function AccessibilityPanel() {
  const { settings, setTheme, setColorBlindMode, setZoom, toggle, resetAccessibility, closePanel } = useAccessibility();
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const groupName = useId();
  // Ref zamiast zależności efektu — closePanel zmienia tożsamość przy każdej zmianie ustawień, a efekt ma działać raz na otwarcie.
  const closeRef = useRef(closePanel);
  closeRef.current = closePanel;

  // Focus trap, Escape i powrót fokusu na element, z którego otwarto panel (przycisk albo miejsce użycia Alt+A).
  useEffect(() => {
    const returnTo = document.activeElement as HTMLElement | null;
    const wrapper = document.getElementById(CONTENT_WRAPPER_ID);
    wrapper?.setAttribute("inert", "");
    panelRef.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus();

    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        closeRef.current();
        return;
      }
      if (e.key !== "Tab" || !panelRef.current) return;
      const items = Array.from(panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (items.length === 0) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      wrapper?.removeAttribute("inert");
      returnTo?.focus();
    };
  }, []);

  return createPortal(
    <div data-a11y-surface className="fixed inset-0 z-50 flex justify-end">
      <div aria-hidden className="a11y-overlay absolute inset-0" onClick={closePanel} />
      <div
        ref={panelRef}
        id={PANEL_ID}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="relative flex h-full w-full max-w-md flex-col overflow-y-auto border-l-4 border-gov-blue bg-white text-gov-text shadow-xl"
      >
        <div className="flex items-center justify-between gap-2 border-b border-slate-200 px-5 py-4">
          <h2 id={titleId} className="text-lg font-bold">
            Ułatwienia dostępu
          </h2>
          <button type="button" className={quickButton} aria-label="Zamknij panel ułatwień dostępu" onClick={closePanel}>
            <span aria-hidden>✕</span>
          </button>
        </div>

        <div className="space-y-6 px-5 py-5 text-sm">
          <RadioGroup
            legend="Motyw kolorystyczny"
            name={`${groupName}-theme`}
            options={THEMES.map((t) => ({ value: t, label: THEME_LABEL[t] }))}
            value={settings.theme}
            onChange={setTheme}
          />
          <RadioGroup
            legend="Tryb dla osób z zaburzeniami widzenia barw"
            name={`${groupName}-cb`}
            options={COLOR_BLIND_MODES.map((m) => ({ value: m, label: COLOR_BLIND_LABEL[m] }))}
            value={settings.colorBlindMode}
            onChange={setColorBlindMode}
          />
          <RadioGroup
            legend="Powiększenie"
            name={`${groupName}-zoom`}
            options={ZOOM_LEVELS.map((z) => ({ value: z, label: `${z}%` }))}
            value={settings.zoom}
            onChange={setZoom}
            inline
          />

          <fieldset>
            <legend className="mb-2 font-bold">Dodatkowe ułatwienia</legend>
            <ul className="space-y-2">
              {A11Y_TOGGLES.map((key) => (
                <li key={key}>
                  <button
                    type="button"
                    aria-pressed={settings[key]}
                    onClick={() => toggle(key)}
                    className="flex w-full items-start gap-3 rounded px-3 py-2 text-left ring-1 ring-inset ring-slate-400 hover:bg-gov-light focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue aria-pressed:ring-2 aria-pressed:ring-gov-blue"
                  >
                    <span
                      aria-hidden
                      className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-sm border-2 border-gov-blue text-xs font-bold ${
                        settings[key] ? "bg-gov-blue text-white" : ""
                      }`}
                    >
                      {settings[key] ? "✓" : ""}
                    </span>
                    <span>
                      <span className="block font-semibold">{TOGGLE_LABEL[key].label}</span>
                      <span className="block text-xs text-slate-700">{TOGGLE_LABEL[key].hint}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </fieldset>

          <button
            type="button"
            onClick={resetAccessibility}
            className="w-full rounded bg-gov-blue px-3.5 py-2 font-bold text-white hover:bg-gov-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue focus-visible:ring-offset-2"
          >
            Przywróć ustawienia domyślne
          </button>
          <p className="text-xs text-slate-700">
            Skrót klawiszowy: <kbd className="rounded border border-slate-400 px-1 font-mono">Alt</kbd> +{" "}
            <kbd className="rounded border border-slate-400 px-1 font-mono">A</kbd> otwiera i zamyka ten panel. Ustawienia są
            zapamiętywane w tej przeglądarce.
          </p>
        </div>
      </div>
    </div>,
    document.body,
  );
}

function RadioGroup<T extends string | number>({
  legend,
  name,
  options,
  value,
  onChange,
  inline = false,
}: {
  legend: string;
  name: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
  inline?: boolean;
}) {
  return (
    <fieldset>
      <legend className="mb-2 font-bold">{legend}</legend>
      <div className={inline ? "flex flex-wrap gap-2" : "space-y-1.5"}>
        {options.map((o) => (
          <label
            key={o.value}
            className="flex cursor-pointer items-center gap-2 rounded px-3 py-1.5 ring-1 ring-inset ring-slate-400 hover:bg-gov-light has-[:checked]:font-bold has-[:checked]:ring-2 has-[:checked]:ring-gov-blue has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-gov-blue"
          >
            <input
              type="radio"
              name={name}
              checked={value === o.value}
              onChange={() => onChange(o.value)}
              className="h-4 w-4 accent-gov-blue focus:outline-none"
            />
            {o.label}
          </label>
        ))}
      </div>
    </fieldset>
  );
}

const iconProps = { "aria-hidden": true, viewBox: "0 0 24 24", className: "h-4 w-4", fill: "none", stroke: "currentColor", strokeWidth: 2 } as const;

function MoonIcon() {
  return (
    <svg {...iconProps}>
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" strokeLinejoin="round" />
    </svg>
  );
}

function SunIcon() {
  return (
    <svg {...iconProps}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" strokeLinecap="round" />
    </svg>
  );
}

function ContrastIcon() {
  return (
    <svg {...iconProps}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 3a9 9 0 0 1 0 18z" fill="currentColor" />
    </svg>
  );
}

function AccessibilityIcon() {
  return (
    <svg {...iconProps}>
      <circle cx="12" cy="4.5" r="1.8" fill="currentColor" stroke="none" />
      <path d="M4 8.5l8 1.5 8-1.5M12 10v4.5M12 14.5L8.5 21M12 14.5l3.5 6.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
