import type { Metadata } from "next";
import "./globals.css";
import { Header } from "@/components/Header";
import { PrivacyNotice } from "@/components/PrivacyNotice";
import { UserProvider } from "@/components/UserProvider";

export const metadata: Metadata = {
  title: "MERITUM — Cyfrowy Asystent Ocen Okresowych",
  description: "Oceny okresowe członków korpusu służby cywilnej — demo KPRM",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pl">
      <body className="min-h-screen">
        <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2">
          Przejdź do treści
        </a>
        <UserProvider>
          <Header />
          <PrivacyNotice />
          <main id="main" className="mx-auto max-w-7xl px-4 py-6">
            {children}
          </main>
        </UserProvider>
        <footer className="border-t border-slate-200 bg-white">
          <div className="mx-auto max-w-7xl px-4 py-4 text-xs text-slate-700">
            MERITUM · Służba Cywilna · demo hackathonowe · treści AI wymagają weryfikacji człowieka (AI Act art. 50)
          </div>
        </footer>
      </body>
    </html>
  );
}
