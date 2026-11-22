"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import {
  A11Y_OPTIONS,
  A11Y_STORAGE_KEY,
  applyA11y,
  DEFAULT_A11Y,
  parseA11y,
  TOGGLE_LABEL,
  ZOOM_LEVELS,
  type A11ySettings,
  type A11yToggle,
  type ColorBlindMode,
  type Theme,
  type ZoomLevel,
} from "@/lib/a11y";

interface AccessibilityContextValue {
  settings: A11ySettings;
  setTheme: (theme: Theme) => void;
  setColorBlindMode: (mode: ColorBlindMode) => void;
  setZoom: (zoom: ZoomLevel) => void;
  zoomIn: () => void;
  zoomOut: () => void;
  toggle: (key: A11yToggle) => void;
  resetAccessibility: () => void;
  /** Komunikat dla czytników ekranu (region aria-live="polite"). */
  announce: (message: string) => void;
  panelOpen: boolean;
  openPanel: () => void;
  closePanel: () => void;
}

const AccessibilityContext = createContext<AccessibilityContextValue | null>(null);

const THEME_ANNOUNCEMENT: Record<Theme, string> = {
  light: "Włączono tryb jasny",
  dark: "Włączono tryb ciemny",
  "hc-yellow": "Włączono wysoki kontrast: żółty tekst na czarnym tle",
  "hc-bw": "Włączono wysoki kontrast: czarny tekst na białym tle",
};

const COLOR_BLIND_ANNOUNCEMENT: Record<ColorBlindMode, string> = {
  none: "Wyłączono tryb dla daltonistów",
  "okabe-ito": "Włączono paletę Okabe-Ito",
  protanopia: "Włączono tryb dla protanopii",
  deuteranopia: "Włączono tryb dla deuteranopii",
  tritanopia: "Włączono tryb dla tritanopii",
};

const ANNOUNCE_DELAY_MS = 50;

export function AccessibilityProvider({ children }: { children: React.ReactNode }) {
  const [settings, setSettings] = useState<A11ySettings>(DEFAULT_A11Y);
  const [loaded, setLoaded] = useState(false);
  const [message, setMessage] = useState("");
  const [panelOpen, setPanelOpen] = useState(false);
  const announceTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => {
    let raw: string | null = null;
    try {
      raw = localStorage.getItem(A11Y_STORAGE_KEY);
    } catch {
      // Brak dostępu do localStorage (tryb prywatny) — zostają ustawienia domyślne.
    }
    setSettings(parseA11y(raw, DEFAULT_A11Y, A11Y_OPTIONS));
    setLoaded(true);
  }, []);

  useEffect(() => {
    if (!loaded) return;
    applyA11y(document.documentElement, settings);
    try {
      localStorage.setItem(A11Y_STORAGE_KEY, JSON.stringify(settings));
    } catch {
      // Ustawienia działają do końca sesji karty.
    }
  }, [settings, loaded]);

  const announce = useCallback((text: string) => {
    // Wyczyszczenie i ponowne ustawienie treści gwarantuje odczytanie także powtórzonego komunikatu.
    setMessage("");
    clearTimeout(announceTimer.current);
    announceTimer.current = setTimeout(() => setMessage(text), ANNOUNCE_DELAY_MS);
  }, []);

  useEffect(() => () => clearTimeout(announceTimer.current), []);

  const update = useCallback(
    (patch: Partial<A11ySettings>, text: string) => {
      setSettings((s) => ({ ...s, ...patch }));
      announce(text);
    },
    [announce],
  );

  const setZoom = useCallback((zoom: ZoomLevel) => update({ zoom }, `Powiększenie ${zoom}%`), [update]);

  const stepZoom = useCallback(
    (direction: 1 | -1) => {
      const index = ZOOM_LEVELS.indexOf(settings.zoom) + direction;
      if (index < 0 || index >= ZOOM_LEVELS.length) {
        announce(direction > 0 ? "Osiągnięto maksymalne powiększenie" : "Osiągnięto minimalne powiększenie");
        return;
      }
      setZoom(ZOOM_LEVELS[index]);
    },
    [settings.zoom, setZoom, announce],
  );

  const value = useMemo<AccessibilityContextValue>(
    () => ({
      settings,
      setTheme: (theme) => update({ theme }, THEME_ANNOUNCEMENT[theme]),
      setColorBlindMode: (colorBlindMode) => update({ colorBlindMode }, COLOR_BLIND_ANNOUNCEMENT[colorBlindMode]),
      setZoom,
      zoomIn: () => stepZoom(1),
      zoomOut: () => stepZoom(-1),
      toggle: (key) => {
        const next = !settings[key];
        update({ [key]: next }, `${next ? "Włączono" : "Wyłączono"}: ${TOGGLE_LABEL[key].label.toLowerCase()}`);
      },
      resetAccessibility: () => {
        setSettings(DEFAULT_A11Y);
        announce("Przywrócono ustawienia domyślne");
      },
      announce,
      panelOpen,
      openPanel: () => setPanelOpen(true),
      closePanel: () => setPanelOpen(false),
    }),
    [settings, update, setZoom, stepZoom, announce, panelOpen],
  );

  // Alt+A otwiera / zamyka panel ułatwień z dowolnego miejsca (e.code — na macOS Option+A daje znak „å”).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.altKey && !e.ctrlKey && !e.metaKey && e.code === "KeyA") {
        e.preventDefault();
        setPanelOpen((open) => !open);
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  return (
    <AccessibilityContext.Provider value={value}>
      {children}
      <div aria-live="polite" aria-atomic="true" className="sr-only">
        {message}
      </div>
    </AccessibilityContext.Provider>
  );
}

export function useAccessibility(): AccessibilityContextValue {
  const ctx = useContext(AccessibilityContext);
  if (!ctx) throw new Error("useAccessibility poza AccessibilityProvider");
  return ctx;
}
