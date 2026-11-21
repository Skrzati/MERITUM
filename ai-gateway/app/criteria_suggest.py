"""Sugestia kryteriów dodatkowych (§ 4 ust. 2) i oczekiwań do rozmowy rozpoczynającej (§ 5 ust. 1 pkt 3).

AI wyłącznie podpowiada — wybór kryteriów należy do oceniającego. Kody spoza katalogu, duplikaty i kryteria
obowiązkowe są odrzucane niezależnie od odpowiedzi LLM.
"""
import re
from typing import Dict, List

from .catalog import CRITERIA
from .guardrails import clean_list, strip_score_proposals
from .llm import MODE_FALLBACK, MODE_MOCK, LLMClient
from .pii import PiiMasker, unmask_pii

MAX_SUGGESTIONS = 3
EXPECTATIONS_PER_CRITERION = 2

SUGGEST_SYSTEM = (
    "Jesteś asystentem przełożonego w służbie cywilnej. Na podstawie opisu stanowiska proponujesz maksymalnie 3 "
    "kryteria dodatkowe WYŁĄCZNIE z podanego katalogu (kody K01–K14), bez kryteriów obowiązkowych. Dla każdego "
    "podajesz krótkie uzasadnienie doboru i 2 przykładowe oczekiwania do omówienia na rozmowie rozpoczynającej. "
    "NIGDY nie proponujesz punktów ani oceny. Odpowiadasz wyłącznie JSON: {\"suggestions\": [{\"code\": \"K..\", "
    "\"reason\": \"..\", \"expectations\": [\"..\", \"..\"]}]}. Zachowuj tokeny w nawiasach kwadratowych bez zmian."
)

# Słowa kluczowe opisu stanowiska wskazujące na kryterium (tryb MOCK — deterministyczny).
_KEYWORD_PATTERNS: Dict[str, str] = {
    "K01": r"komunikac|korespondenc|pism|informac",
    "K02": r"innowac|usprawni|projekt|cyfryzac|digitali",
    "K03": r"negocjac|uzgodni|porozumie|kontrahent|umow",
    "K05": r"interesan|klient|obsług|petent|skarg|wnios",
    "K06": r"decyzj|rozstrzyg|zatwierdz|odpowiedzialn",
    "K07": r"kryzys|zarządzani\w* kryzysow|awari|incydent|bezpieczeństw",
    "K08": r"kontrol|audyt|rozlicz|finans|budżet|księgow|dokumentac",
    "K09": r"anali|dane|statysty|raport|prognoz",
    "K10": r"prawn|legislac|przepis|specjalist|ekspert",
    "K11": r"zespoł|współprac|międzywydział|koordyn",
    "K12": r"prezentac|szkoleni|wystąpie|konferenc|media|rzecznik",
    "K13": r"kierowni|zarządzani\w* zespoł|naczelni|podległ",
    "K14": r"strateg|planowani|polityk|programow",
}
KEYWORDS: Dict[str, re.Pattern] = {code: re.compile(p, re.IGNORECASE) for code, p in _KEYWORD_PATTERNS.items()}
DEFAULT_ORDER = ["K08", "K11", "K04", "K10", "K01", "K09"]

EXPECTATIONS: Dict[str, List[str]] = {
    "K01": ["Pisma i informacje przygotowywane prostym językiem, zrozumiałe dla adresata",
            "Bieżące informowanie przełożonego o stanie spraw i ryzykach"],
    "K02": ["Zgłoszenie co najmniej jednej propozycji usprawnienia procesu w okresie oceny",
            "Otwartość na nowe narzędzia i sposoby realizacji zadań"],
    "K03": ["Przygotowanie stanowiska negocjacyjnego przed uzgodnieniami",
            "Dążenie do rozwiązań akceptowalnych dla stron z zachowaniem interesu urzędu"],
    "K04": ["Realizacja zadań w uzgodnionych terminach z sygnalizowaniem zagrożeń",
            "Samodzielne ustalanie priorytetów przy zadaniach równoległych"],
    "K05": ["Udzielanie interesantom rzetelnych informacji w ustawowych terminach",
            "Uprzejmy i profesjonalny kontakt, także w trudnych sytuacjach"],
    "K06": ["Podejmowanie decyzji w granicach upoważnienia na podstawie analizy faktów",
            "Ponoszenie odpowiedzialności za skutki podjętych rozstrzygnięć"],
    "K07": ["Znajomość i stosowanie procedur na wypadek sytuacji kryzysowych",
            "Zachowanie spokoju i skuteczności działania pod presją czasu"],
    "K08": ["Dokumenty przygotowywane zgodnie z przepisami i standardami komórki",
            "Weryfikacja kompletności i poprawności przed przekazaniem dalej"],
    "K09": ["Opieranie rekomendacji na danych i analizie przyczyn problemu",
            "Przygotowywanie analiz z jasno wskazanymi wnioskami"],
    "K10": ["Bieżące śledzenie zmian przepisów w obszarze zadań",
            "Dzielenie się wiedzą ze współpracownikami"],
    "K11": ["Aktywny udział w pracach zespołowych i międzywydziałowych",
            "Terminowe przekazywanie informacji potrzebnych innym komórkom"],
    "K12": ["Przygotowanie i przeprowadzenie prezentacji wyników pracy",
            "Dostosowanie formy wystąpienia do odbiorców"],
    "K13": ["Jasne delegowanie zadań i udzielanie regularnej informacji zwrotnej",
            "Wspieranie rozwoju podległych pracowników"],
    "K14": ["Udział w planowaniu działań komórki w perspektywie rocznej",
            "Powiązanie zadań zespołu z celami urzędu"],
}


def suggest_criteria(llm: LLMClient, position_title: str, department: str, sheet_type: str,
                     mandatory_codes: List[str], responsibilities_summary: str, job_description: str = "") -> Dict:
    masker = PiiMasker(known_units=[department] if department else [])
    masked_title = masker.mask(position_title)
    masked_department = masker.mask(department)
    masked_summary = masker.mask(responsibilities_summary)
    masked_job = masker.mask(job_description)
    mandatory = {c.upper() for c in mandatory_codes}
    available = {code: name for code, name in CRITERIA.items() if code not in mandatory}
    catalog_lines = "\n".join(f"- {code}: {name}" for code, name in available.items())
    prompt = (f"Stanowisko: {masked_title}\nKomórka: {masked_department or '(brak)'}\nArkusz: {sheet_type}\n"
              f"Kryteria obowiązkowe (nie proponuj): {', '.join(sorted(mandatory)) or '(brak)'}\n"
              f"Zakres obowiązków: {masked_summary or '(brak opisu)'}\n"
              f"Opis stanowiska pracy (art. 84 ustawy o służbie cywilnej): {masked_job or '(brak opisu)'}\n"
              f"Katalog kryteriów do wyboru:\n{catalog_lines}\n"
              "Zaproponuj maksymalnie 3 kryteria dodatkowe z uzasadnieniem i oczekiwaniami.")

    answer = llm.chat_json(SUGGEST_SYSTEM, prompt)
    suggestions = _valid(answer.get("suggestions") if answer else None, available)
    if suggestions:
        mode = llm.mode
    else:
        mode = MODE_MOCK if llm.mode == MODE_MOCK else MODE_FALLBACK
        suggestions = _mock(f"{position_title} {department} {responsibilities_summary} {job_description}", available)

    unmask = lambda s: unmask_pii(str(s), masker.mapping)  # noqa: E731
    return {
        "masked_prompt": prompt,
        "suggestions": [{
            "code": s["code"],
            "name": CRITERIA[s["code"]],
            "reason": unmask(strip_score_proposals(s["reason"])),
            "expectations": [unmask(e) for e in clean_list(s["expectations"], strip_score_proposals)]
                            [:EXPECTATIONS_PER_CRITERION],
        } for s in suggestions],
        "mode": mode,
    }


def _valid(raw, available: Dict[str, str]) -> List[Dict]:
    if not isinstance(raw, list):
        return []
    seen, out = set(), []
    for item in raw:
        if not isinstance(item, dict):
            continue
        code = str(item.get("code", "")).upper()
        if code not in available or code in seen:
            continue
        expectations = [str(e) for e in item.get("expectations") or [] if str(e).strip()]
        seen.add(code)
        out.append({"code": code, "reason": str(item.get("reason", "")),
                    "expectations": expectations or EXPECTATIONS[code]})
    return out[:MAX_SUGGESTIONS]


def _mock(description: str, available: Dict[str, str]) -> List[Dict]:
    hits = [(len(KEYWORDS[c].findall(description)), c) for c in available if c in KEYWORDS]
    ranked = [c for count, c in sorted(hits, key=lambda h: (-h[0], h[1])) if count > 0]
    ranked += [c for c in DEFAULT_ORDER + list(available) if c in available and c not in ranked]
    return [{
        "code": code,
        "reason": (f"Opis stanowiska wskazuje na zadania wymagające kompetencji „{available[code]}”."
                   if KEYWORDS.get(code) and KEYWORDS[code].search(description)
                   else f"Kryterium „{available[code]}” uzupełnia obowiązkowe kryteria arkusza."),
        "expectations": EXPECTATIONS[code],
    } for code in ranked[:MAX_SUGGESTIONS]]
