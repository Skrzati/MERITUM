/**
 * Ustawienia dostępności cyfrowej (WCAG 2.1 AA+, ustawa o dostępności cyfrowej).
 * Moduł bez „use client” — używany zarówno przez skrypt startowy w layout.tsx (bez migotania motywu), jak i przez provider.
 */

export const THEMES = ["light", "dark", "hc-yellow", "hc-bw"] as const;
export const COLOR_BLIND_MODES = ["none", "okabe-ito", "protanopia", "deuteranopia", "tritanopia"] as const;
export const ZOOM_LEVELS = [85, 100, 115, 130, 150, 175] as const;
export const A11Y_TOGGLES = ["dyslexiaFriendly", "underlineLinks", "reducedMotion", "enhancedFocus"] as const;

export type Theme = (typeof THEMES)[number];
export type ColorBlindMode = (typeof COLOR_BLIND_MODES)[number];
export type ZoomLevel = (typeof ZOOM_LEVELS)[number];
export type A11yToggle = (typeof A11Y_TOGGLES)[number];

export type A11ySettings = {
  theme: Theme;
  colorBlindMode: ColorBlindMode;
  zoom: ZoomLevel;
} & Record<A11yToggle, boolean>;

export const DEFAULT_A11Y: A11ySettings = {
  theme: "light",
  colorBlindMode: "none",
  zoom: 100,
  dyslexiaFriendly: false,
  underlineLinks: false,
  reducedMotion: false,
  enhancedFocus: false,
};

export const A11Y_STORAGE_KEY = "meritum:a11y";

export const A11Y_OPTIONS = { themes: THEMES, colorBlindModes: COLOR_BLIND_MODES, zoomLevels: ZOOM_LEVELS, toggles: A11Y_TOGGLES };

export const THEME_LABEL: Record<Theme, string> = {
  light: "Jasny",
  dark: "Ciemny",
  "hc-yellow": "Wysoki kontrast — żółty na czarnym",
  "hc-bw": "Wysoki kontrast — czarny na białym",
};

export const COLOR_BLIND_LABEL: Record<ColorBlindMode, string> = {
  none: "Brak (kolory standardowe)",
  "okabe-ito": "Paleta Okabe-Ito (wysoka rozróżnialność)",
  protanopia: "Protanopia (czerwień)",
  deuteranopia: "Deuteranopia (zieleń)",
  tritanopia: "Tritanopia (błękit)",
};

export const TOGGLE_LABEL: Record<A11yToggle, { label: string; hint: string }> = {
  dyslexiaFriendly: { label: "Tekst przyjazny dysleksji", hint: "Większe odstępy liter, słów i wierszy, wyrównanie do lewej." },
  underlineLinks: { label: "Podkreślanie linków", hint: "Wyraźne podkreślenie linków i przycisków nawigacji." },
  reducedMotion: { label: "Ograniczenie animacji", hint: "Wyłącza animacje i przejścia." },
  enhancedFocus: { label: "Wzmocniony obrys fokusu", hint: "Gruby, dwukolorowy obrys przy nawigacji klawiaturą." },
};

export function isHighContrast(theme: Theme): boolean {
  return theme === "hc-yellow" || theme === "hc-bw";
}

/**
 * Odczyt ustawień z localStorage z walidacją każdego pola.
 * Funkcja samowystarczalna (bez odwołań do zmiennych modułu) — jej treść jest wstrzykiwana do skryptu startowego.
 */
export function parseA11y(raw: string | null, defaults: A11ySettings, options: typeof A11Y_OPTIONS): A11ySettings {
  const result: A11ySettings = Object.assign({}, defaults);
  if (!raw) return result;
  let data: Record<string, unknown>;
  try {
    data = JSON.parse(raw);
  } catch {
    return result;
  }
  if (!data || typeof data !== "object") return result;
  if ((options.themes as readonly unknown[]).includes(data.theme)) result.theme = data.theme as Theme;
  if ((options.colorBlindModes as readonly unknown[]).includes(data.colorBlindMode)) result.colorBlindMode = data.colorBlindMode as ColorBlindMode;
  if ((options.zoomLevels as readonly unknown[]).includes(data.zoom)) result.zoom = data.zoom as ZoomLevel;
  for (const key of options.toggles) if (typeof data[key] === "boolean") result[key] = data[key] as boolean;
  return result;
}

/** Atrybuty data-* i zmienne CSS na <html>. Funkcja samowystarczalna — patrz parseA11y. */
export function applyA11y(root: HTMLElement, s: A11ySettings): void {
  const on = (v: boolean) => (v ? "on" : "off");
  const highContrast = s.theme === "hc-yellow" || s.theme === "hc-bw";
  root.dataset.theme = s.theme;
  root.dataset.cb = s.colorBlindMode;
  root.dataset.dyslexia = on(s.dyslexiaFriendly);
  root.dataset.underlineLinks = on(s.underlineLinks || highContrast);
  root.dataset.reducedMotion = on(s.reducedMotion);
  root.dataset.enhancedFocus = on(s.enhancedFocus);
  // Statusy nigdy nie mogą polegać wyłącznie na kolorze, gdy kolory są zmienione lub zredukowane.
  root.dataset.statusShapes = on(highContrast || s.colorBlindMode !== "none");
  root.style.setProperty("--a11y-zoom", String(s.zoom));
  root.style.colorScheme = s.theme === "dark" || s.theme === "hc-yellow" ? "dark" : "light";
}

/** Skrypt wykonywany przed hydratacją — ustawienia obowiązują od pierwszej klatki. */
export const A11Y_BOOT_SCRIPT = `(function(){try{var s=(${parseA11y.toString()})(localStorage.getItem(${JSON.stringify(
  A11Y_STORAGE_KEY,
)}),${JSON.stringify(DEFAULT_A11Y)},${JSON.stringify(A11Y_OPTIONS)});(${applyA11y.toString()})(document.documentElement,s);}catch(e){}})();`;
