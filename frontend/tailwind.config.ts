import type { Config } from "tailwindcss";

/**
 * Wszystkie kolory czytają zmienne CSS motywu (globals.css: jasny, ciemny, 2 tryby wysokiego kontrastu, Okabe-Ito).
 * Istniejące klasy (bg-white, text-slate-700, bg-gov-blue…) przełączają się więc z motywem bez przepisywania widoków.
 */
const v = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;

const gov = {
  blue: v("accent"),
  hover: v("accent-hover"),
  light: v("accent-soft"),
  red: v("brand-red"),
  text: v("text"),
  section: v("bg"),
};

/** Skala odcieni przemapowana na tokeny semantyczne (jasne odcienie = tło, środkowe = obramowanie, ciemne = tekst). */
function scale(soft: string, line: string, ink: string) {
  return Object.fromEntries(
    [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950].map((shade) => [shade, v(shade <= 100 ? soft : shade <= 400 ? line : ink)]),
  );
}

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        white: v("surface"),
        gov,
        // Tokeny navy z wcześniejszych ekranów przemapowane na niebieski gov.pl — spójny wygląd bez przepisywania widoków.
        navy: {
          50: v("accent-soft"),
          100: v("accent-soft-strong"),
          200: v("accent"),
          600: v("accent"),
          700: v("accent"),
          800: v("accent-hover"),
          900: v("accent-ink"),
        },
        slate: {
          50: v("surface-2"),
          100: v("surface-2"),
          200: v("border"),
          300: v("border"),
          400: v("border-strong"),
          500: v("text-muted"),
          600: v("text-muted"),
          700: v("text-muted"),
          800: v("text"),
          900: v("text"),
          950: v("text"),
        },
        red: scale("status-err-soft", "status-err", "status-err"),
        green: scale("status-ok-soft", "status-ok", "status-ok"),
        amber: { ...scale("status-warn-soft", "status-warn", "status-warn"), 200: v("highlight"), 950: v("highlight-ink") },
        ui: {
          bg: v("bg"),
          surface: v("surface"),
          "surface-2": v("surface-2"),
          text: v("text"),
          muted: v("text-muted"),
          border: v("border"),
          accent: v("accent"),
        },
        status: {
          ok: v("status-ok"),
          "ok-soft": v("status-ok-soft"),
          warn: v("status-warn"),
          "warn-soft": v("status-warn-soft"),
          err: v("status-err"),
          "err-soft": v("status-err-soft"),
          info: v("accent"),
          "info-soft": v("accent-soft"),
        },
      },
      fontFamily: { sans: ["Lato", "system-ui", "-apple-system", "Segoe UI", "Roboto", "Noto Sans", "Arial", "sans-serif"] },
    },
  },
  plugins: [],
};

export default config;
