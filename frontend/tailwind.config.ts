import type { Config } from "tailwindcss";

/** Paleta urzędowa gov.pl (kontrast WCAG 2.1 AA na białym tle). */
const gov = {
  blue: "#0052A5",
  hover: "#003B78",
  light: "#E6F0FA",
  red: "#D4213D",
  text: "#1B1B1B",
  section: "#F5F6F7",
};

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        gov,
        // Tokeny navy z wcześniejszych ekranów przemapowane na niebieski gov.pl — spójny wygląd bez przepisywania widoków.
        navy: {
          50: gov.light,
          100: "#CCE0F5",
          200: "#99C2EB",
          600: "#0A5CB0",
          700: gov.blue,
          800: gov.hover,
          900: "#002A57",
        },
      },
      fontFamily: { sans: ["Lato", "system-ui", "-apple-system", "Segoe UI", "Roboto", "Noto Sans", "Arial", "sans-serif"] },
    },
  },
  plugins: [],
};

export default config;
