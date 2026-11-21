"use client";

import { useEffect, useState } from "react";

const DISMISSED_KEY = "meritum.privacyNoticeDismissed";

/** Klauzula informacyjna RODO (art. 13) i AI Act (art. 26, 50) — zamykana, zapamiętywana w przeglądarce. */
export function PrivacyNotice() {
  const [visible, setVisible] = useState(false);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    try {
      setVisible(window.localStorage.getItem(DISMISSED_KEY) !== "1");
    } catch {
      setVisible(true);
    }
  }, []);

  const dismiss = () => {
    try {
      window.localStorage.setItem(DISMISSED_KEY, "1");
    } catch {
      /* brak dostępu do storage — zamknięcie tylko na tę sesję */
    }
    setVisible(false);
  };

  if (!visible) return null;
  return (
    <aside aria-label="Informacja o przetwarzaniu danych" className="border-b border-navy-200 bg-gov-light">
      <div className="mx-auto max-w-7xl px-4 py-2 text-sm text-gov-text">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p>
            <strong>RODO (art. 13) i AI Act (art. 26, 50):</strong> dane z oceny okresowej przetwarza urząd jako pracodawca;
            AI tylko podpowiada i nie wystawia oceny.
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setExpanded((e) => !e)}
              aria-expanded={expanded}
              className="rounded px-2 py-1 font-semibold text-gov-blue underline hover:bg-white focus:outline-none focus-visible:ring-2 focus-visible:ring-gov-blue"
            >
              {expanded ? "Zwiń" : "Szczegóły"}
            </button>
            <button
              type="button"
              onClick={dismiss}
              className="rounded bg-gov-blue px-3 py-1 font-semibold text-white hover:bg-gov-hover focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-gov-blue"
            >
              Rozumiem
            </button>
          </div>
        </div>
        {expanded && (
          <ul className="mt-2 list-disc space-y-1 pl-5 text-xs text-slate-800">
            <li>
              <strong>Notatki</strong> z okresu oceny prowadzi i widzi oceniający; oceniany widzi notatki oznaczone do
              udostępnienia dopiero po sporządzeniu oceny. Kadry i DG widzą arkusz, nie notatki.
            </li>
            <li>
              <strong>Pseudonimizacja:</strong> przed wysłaniem do modelu AI imiona, nazwiska, e-maile, PESEL i nazwy komórek są
              zastępowane tokenami (np. [OSOBA_1]); podgląd „Co widzi model AI?” pokazuje dokładny tekst.
            </li>
            <li>
              <strong>AI nie wystawia oceny</strong> ani nie proponuje punktów — o ocenie decyduje oceniający, a każda treść AI jest
              oznaczona (art. 50). Każde wywołanie AI jest rejestrowane w dzienniku audytu (art. 26).
            </li>
            <li>
              Ankieta dobrostanu jest dobrowolna, niepowiązana z oceną i raportowana wyłącznie zbiorczo (k-anonimizacja). Prawo
              dostępu do danych (art. 15 RODO) realizują kadry.
            </li>
          </ul>
        )}
      </div>
    </aside>
  );
}
