import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        navy: { 50: "#eef2f8", 100: "#d9e1ee", 600: "#1f3a68", 700: "#172d52", 800: "#10213d", 900: "#0b1830" },
      },
      fontFamily: { sans: ["system-ui", "-apple-system", "Segoe UI", "Roboto", "Noto Sans", "Arial", "sans-serif"] },
    },
  },
  plugins: [],
};

export default config;
