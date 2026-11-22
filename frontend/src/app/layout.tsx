import type { Metadata } from "next";
import "./globals.css";
import { A11Y_BOOT_SCRIPT } from "@/lib/a11y";
import { AccessibilityProvider } from "@/components/AccessibilityProvider";
import { ColorBlindFilters } from "@/components/ColorBlindFilters";
import { Header } from "@/components/Header";
import { PrivacyNotice } from "@/components/PrivacyNotice";
import { UserProvider } from "@/components/UserProvider";

export const metadata: Metadata = {
  title: "MERITUM — Cyfrowy Asystent Ocen Okresowych",
  description: "Oceny okresowe członków korpusu służby cywilnej — demo KPRM",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    // Atrybuty data-* na <html> ustawia skrypt startowy przed hydratacją — stąd suppressHydrationWarning.
    <html lang="pl" data-theme="light" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: A11Y_BOOT_SCRIPT }} />
      </head>
      <body className="min-h-screen">
        <a
          href="#main-content"
          className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-[60] focus:rounded focus:bg-white focus:px-3 focus:py-2 focus:font-bold focus:text-gov-blue focus:outline focus:outline-2 focus:outline-gov-blue"
        >
          Przejdź do głównej treści
        </a>
        <ColorBlindFilters />
        <AccessibilityProvider>
          <div id="app-content-wrapper" className="flex min-h-screen flex-col">
            <UserProvider>
              <Header />
              <PrivacyNotice />
              <main id="main-content" tabIndex={-1} className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 focus:outline-none">
                {children}
              </main>
            </UserProvider>
            <footer className="border-t border-slate-200 bg-white">
              <div className="mx-auto max-w-7xl px-4 py-4 text-xs text-slate-700">
                MERITUM · Służba Cywilna · demo hackathonowe · treści AI wymagają weryfikacji człowieka (AI Act art. 50)
              </div>
            </footer>
          </div>
        </AccessibilityProvider>
      </body>
    </html>
  );
}
