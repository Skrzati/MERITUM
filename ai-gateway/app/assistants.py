"""Asystenci AI: sokratejski asystent uzasadnień i HR-partner. LLM widzi wyłącznie tekst po maskowaniu PII."""
import re
from dataclasses import dataclass
from typing import Dict, List, Optional

from .catalog import CRITERIA, DEVELOPMENT_ACTIONS, GENERAL_DEVELOPMENT_AREA, development_70_20_10
from .guardrails import clean_list, compliance_meta, strip_medical_content, strip_score_proposals
from .llm import MODE_FALLBACK, MODE_MOCK, LLMClient
from .pii import PiiMasker, unmask_pii

MAX_QUESTIONS = 6
NOTE_SNIPPET_LEN = 160
OVERLOAD_RE = re.compile(r"nadgodzin|przeciąż|brak czasu|zastępstw|L4|zmęcz|równolegle|po godzinach", re.IGNORECASE)
APPRECIATION_RE = re.compile(r"dotrzyman|wdroż|przejęł|przejął|sukces|pochwał|zrealizowa|terminowo", re.IGNORECASE)

OVERTIME_HOURS_THRESHOLD = 20
CONCURRENT_PROJECTS_THRESHOLD = 3
OVERLOAD_NOTES_THRESHOLD = 2

SOCRATIC_SYSTEM = (
    "Jesteś asystentem przełożonego w służbie cywilnej. Zadajesz pytania sokratejskie, które pomagają "
    "oprzeć uzasadnienie oceny okresowej na faktach. NIGDY nie proponujesz punktów ani oceny. "
    "Odpowiadasz wyłącznie JSON: {\"socratic_questions\": [..], \"draft_justification\": \"..\"}. "
    "Zachowuj tokeny w nawiasach kwadratowych (np. [OSOBA_1]) bez zmian."
)
HR_SYSTEM = (
    "Jesteś HR-partnerem wspierającym przełożonego. Podajesz wyłącznie sugestie wspierające i organizacyjne, "
    "bez diagnoz i treści medycznych. Odpowiadasz wyłącznie JSON: {\"conversation_starters\": [..], "
    "\"organizational_suggestions\": [..], \"appreciation_moment\": \"..\"}. Zachowuj tokeny [..] bez zmian."
)


@dataclass
class CriterionScore:
    name: str
    score: Optional[int] = None
    code: Optional[str] = None


def _snippet(text: str) -> str:
    text = " ".join(text.split())
    return text if len(text) <= NOTE_SNIPPET_LEN else text[:NOTE_SNIPPET_LEN].rsplit(" ", 1)[0] + "…"


def socratic_draft(llm: LLMClient, employee_name: str, sheet_type: str, criteria: List[CriterionScore],
                   notes: List[str]) -> Dict:
    masker = PiiMasker(known_names=[employee_name])
    person = masker.mask(employee_name)
    masked_notes = [masker.mask(n) for n in notes]
    criteria_lines = "\n".join(f"- {masker.mask(c.name)}: {c.score if c.score is not None else 'brak'}" for c in criteria)
    notes_lines = "\n".join(f"- {n}" for n in masked_notes) or "- (brak notatek)"
    prompt = (f"Oceniany: {person}\nArkusz: {sheet_type}\nKryteria i oceny przełożonego:\n{criteria_lines}\n"
              f"Notatki z okresu oceny:\n{notes_lines}\n"
              "Zadaj pytania pomocnicze i przygotuj szkic uzasadnienia opartego na faktach.")

    answer = llm.chat_json(SOCRATIC_SYSTEM, prompt)
    if answer and isinstance(answer.get("socratic_questions"), list) and answer.get("draft_justification"):
        mode, questions, draft = llm.mode, answer["socratic_questions"], str(answer["draft_justification"])
    else:
        mode = MODE_MOCK if llm.mode == MODE_MOCK else MODE_FALLBACK
        questions, draft = _mock_socratic(person, criteria, masked_notes)

    questions = clean_list(questions, strip_score_proposals)[:MAX_QUESTIONS]
    return {
        "masked_prompt": prompt,
        "socratic_questions": [unmask_pii(q, masker.mapping) for q in questions],
        "draft_justification": unmask_pii(strip_score_proposals(draft), masker.mapping),
        "mode": mode,
        "complianceMeta": compliance_meta(masker.redacted_count),
    }


def _mock_socratic(person: str, criteria: List[CriterionScore], notes: List[str]):
    questions = []
    for c in criteria:
        if c.score in (1, 9):
            questions.append(f"Jakie konkretne sytuacje z całego okresu oceny potwierdzają, że w kryterium "
                             f"„{c.name}” wyniki tak wyraźnie odbiegały od oczekiwań?")
        elif c.score == 3:
            questions.append(f"Jakie wsparcie (szkolenie, mentoring, zmiana organizacji pracy) pomogłoby w kryterium "
                             f"„{c.name}” i czy oczekiwania były wcześniej jasno zakomunikowane?")
        elif c.score == 7:
            questions.append(f"Który przykład najlepiej pokazuje ponadprzeciętne wyniki w kryterium „{c.name}”?")
        elif c.score is None:
            questions.append(f"Które obserwacje z notatek odnoszą się do kryterium „{c.name}”?")
    if any(OVERLOAD_RE.search(n) for n in notes):
        questions.insert(0, "Czy przy ocenie uwzględniono okresowe przeciążenie (nadgodziny, zastępstwa) "
                            "i jego wpływ na wyniki?")
    questions.append("Czy uzasadnienie odwołuje się do faktów z całego okresu oceny, a nie tylko z ostatnich "
                     "tygodni (efekt świeżości)?")

    strong = [c.name for c in criteria if c.score is not None and c.score >= 7]
    standard = [c.name for c in criteria if c.score == 5]
    develop = [c.name for c in criteria if c.score is not None and c.score <= 3]
    parts = [f"W okresie objętym oceną {person} realizował(a) powierzone zadania zgodnie z opisem stanowiska."]
    if strong:
        parts.append("Mocną stroną są kryteria: " + ", ".join(strong) + ".")
    if standard:
        parts.append("Na poziomie oczekiwań realizowane były kryteria: " + ", ".join(standard) + ".")
    if develop:
        parts.append("Obszary do rozwoju wymagające wsparcia w IPRZ: " + ", ".join(develop) + ".")
    if notes:
        parts.append("W notatkach z okresu oceny odnotowano m.in.: " + "; ".join(_snippet(n) for n in notes[:2]))
    parts.append("[Uzupełnij konkretnymi przykładami i datami zdarzeń.]")
    return questions, " ".join(parts)


def hr_partner(llm: LLMClient, notes: List[str], workload_signals: Dict[str, float]) -> Dict:
    masker = PiiMasker()
    masked_notes = [masker.mask(n) for n in notes]
    signals = workload_signals or {}
    burnout = _burnout_signal(masked_notes, signals)
    signals_lines = "\n".join(f"- {masker.mask(k)}: {v}" for k, v in sorted(signals.items())) or "- (brak)"
    notes_lines = "\n".join(f"- {n}" for n in masked_notes) or "- (brak notatek)"
    prompt = (f"Notatki przełożonego:\n{notes_lines}\nSygnały obciążenia:\n{signals_lines}\n"
              f"Sygnał ryzyka przeciążenia: {'TAK' if burnout else 'NIE'}\n"
              "Zaproponuj otwarcia rozmowy, działania organizacyjne i moment docenienia.")

    answer = llm.chat_json(HR_SYSTEM, prompt)
    if answer and isinstance(answer.get("conversation_starters"), list):
        mode = llm.mode
        starters = answer.get("conversation_starters", [])
        suggestions = answer.get("organizational_suggestions", [])
        appreciation = str(answer.get("appreciation_moment", ""))
    else:
        mode = MODE_MOCK if llm.mode == MODE_MOCK else MODE_FALLBACK
        starters, suggestions, appreciation = _mock_hr(masked_notes, signals, burnout)

    unmask = lambda s: unmask_pii(s, masker.mapping)  # noqa: E731
    return {
        "masked_prompt": prompt,
        "burnout_risk_signal": burnout,
        "conversation_starters": [unmask(s) for s in clean_list(starters, strip_medical_content)],
        "organizational_suggestions": [unmask(s) for s in clean_list(suggestions, strip_medical_content)],
        "appreciation_moment": unmask(strip_medical_content(appreciation)),
        "mode": mode,
        "complianceMeta": compliance_meta(masker.redacted_count),
    }


def _burnout_signal(notes: List[str], signals: Dict[str, float]) -> bool:
    overload_notes = sum(1 for n in notes if OVERLOAD_RE.search(n))
    return (signals.get("overtime_hours", 0) >= OVERTIME_HOURS_THRESHOLD
            or signals.get("concurrent_projects", 0) >= CONCURRENT_PROJECTS_THRESHOLD
            or overload_notes >= OVERLOAD_NOTES_THRESHOLD)


def _mock_hr(notes: List[str], signals: Dict[str, float], burnout: bool):
    starters = ["Jak oceniasz swoje obciążenie w ostatnich tygodniach — co zabiera najwięcej energii?",
                "Czego potrzebujesz ode mnie, żeby realizować priorytety spokojniej?"]
    if burnout:
        starters.append("Które zadania możemy przesunąć w czasie lub przekazać, żeby odciążyć Twój kalendarz?")
    if signals.get("missed_trainings", 0) > 0:
        starters.append("Co przeszkodziło w udziale w szkoleniach z IPRZ i jak możemy zarezerwować na nie czas?")

    suggestions = ["Zaplanować krótką rozmowę wspierającą 1:1 w ciągu najbliższych 2 tygodni."]
    if signals.get("overtime_hours", 0) >= OVERTIME_HOURS_THRESHOLD or any("nadgodzin" in n.lower() for n in notes):
        suggestions.append("Przejrzeć rozkład zadań w zespole i umożliwić odbiór nadgodzin w czasie wolnym.")
    if signals.get("concurrent_projects", 0) >= CONCURRENT_PROJECTS_THRESHOLD:
        suggestions.append("Ustalić priorytety — maksymalnie 2 projekty o wysokim priorytecie prowadzone równolegle.")
    if any(re.search(r"zastępstw|L4|nieobecn", n, re.IGNORECASE) for n in notes):
        suggestions.append("Formalnie uregulować zastępstwo lub podział obowiązków nieobecnego członka zespołu.")
    if signals.get("missed_trainings", 0) > 0:
        suggestions.append("Zablokować w kalendarzu czas na szkolenia przewidziane w IPRZ.")

    positive = (next((n for n in notes if APPRECIATION_RE.search(n) and "ryzyk" not in n.lower()), None)
                or next((n for n in notes if APPRECIATION_RE.search(n)), None))
    appreciation = (f"Warto docenić wprost: {_snippet(positive)}" if positive
                    else "Warto docenić konkretny niedawny wkład pracownika w realizację zadań zespołu.")
    return starters, suggestions, appreciation


# ---------------------------------------------------------------- przygotowanie do rozmów (§ 5 ust. 1, § 10)

STAGE_INITIAL, STAGE_EVALUATION, STAGE_FEEDBACK = "INITIAL", "EVALUATION", "FEEDBACK"
STAGE_KIND = {STAGE_INITIAL: "rozpoczynająca (§ 5 ust. 1)", STAGE_EVALUATION: "oceniająca (§ 10)",
              STAGE_FEEDBACK: "bieżąca informacja zwrotna (Standardy ZZL, struktura FUKO)"}
MAX_DEVELOPMENT_CRITERIA = 4

INTERVIEW_SYSTEM = (
    "Jesteś asystentem przełożonego w służbie cywilnej. Przygotowujesz plan rozmowy w ramach oceny okresowej "
    "zgodnie z § 5 ust. 1 (rozmowa rozpoczynająca, 4 zagadnienia) albo § 10 (rozmowa oceniająca, 3 zagadnienia) "
    "rozporządzenia PRM z 4.04.2016. NIGDY nie proponujesz punktów ani oceny. Odpowiadasz wyłącznie JSON: "
    "{\"goal\": \"..\", \"agenda\": [{\"topic\": \"..\", \"legal_basis\": \"..\", \"questions\": [..]}], "
    "\"difficult_situations\": [{\"situation\": \"..\", \"tip\": \"..\"}]}. Zachowuj tokeny [..] bez zmian."
)


def interview_prep(llm: LLMClient, employee_name: str, sheet_type: str, stage: str, criteria: List[CriterionScore],
                   notes: List[str], difficult_scenario: Optional[str] = None) -> Dict:
    masker = PiiMasker(known_names=[employee_name])
    person = masker.mask(employee_name)
    masked_notes = [masker.mask(n) for n in notes]
    kind = STAGE_KIND[stage]
    criteria_lines = "\n".join(f"- {masker.mask(c.name)}" + (f": {c.score}" if c.score is not None else "")
                               for c in criteria)
    notes_lines = "\n".join(f"- {n}" for n in masked_notes) or "- (brak notatek)"
    scenario_line = f"Trudna rozmowa: {DIFFICULT_SCENARIOS[difficult_scenario]['title']}\n" if difficult_scenario else ""
    prompt = (f"Rozmowa: {kind}\nOceniany: {person}\nArkusz: {sheet_type}\nKryteria:\n{criteria_lines or '- (brak)'}\n"
              f"Notatki z okresu oceny:\n{notes_lines}\n{scenario_line}"
              "Przygotuj cel rozmowy, agendę z pytaniami i wskazówki.")

    answer = llm.chat_json(INTERVIEW_SYSTEM, prompt)
    if answer and isinstance(answer.get("agenda"), list) and answer.get("goal"):
        mode = llm.mode
        goal, agenda = str(answer["goal"]), answer["agenda"]
        situations = answer.get("difficult_situations") or []
    else:
        mode = MODE_MOCK if llm.mode == MODE_MOCK else MODE_FALLBACK
        goal, agenda, situations = _mock_interview(stage, criteria, masked_notes)

    unmask = lambda s: unmask_pii(str(s), masker.mapping)  # noqa: E731
    clean_agenda = [{
        "topic": unmask(item.get("topic", "")),
        "legal_basis": str(item.get("legal_basis", "")),
        "questions": [unmask(q) for q in clean_list(item.get("questions", []), strip_score_proposals)],
    } for item in agenda if isinstance(item, dict)]
    clean_situations = [{"situation": unmask(s.get("situation", "")), "tip": unmask(strip_medical_content(str(s.get("tip", ""))))}
                        for s in situations if isinstance(s, dict)]
    return {
        "masked_prompt": prompt,
        "stage": stage,
        "goal": unmask(goal),
        "agenda": clean_agenda,
        "difficult_situations": [s for s in clean_situations if s["tip"]],
        "development_catalog": _development_catalog(criteria),
        "difficult_conversation": _difficult_conversation(difficult_scenario),
        "mode": mode,
        "complianceMeta": compliance_meta(masker.redacted_count),
    }


def _mock_feedback(notes: List[str]):
    """Krótka rozmowa informacyjna (FUKO: Fakty, Ustosunkowanie, Konsekwencje, Oczekiwania) + docenienie z notatek."""
    praise = next((_snippet(n) for n in reversed(notes) if APPRECIATION_RE.search(n)), None)
    fact = _snippet(notes[-1]) if notes else None
    goal = "Krótka bieżąca informacja zwrotna: docenienie, omówienie jednej sytuacji na faktach i uzgodnienie kolejnego kroku."
    agenda = [
        {"topic": "Docenienie", "legal_basis": "Standardy ZZL — kultura doceniania",
         "questions": [f"Chcę docenić: „{praise}”. Co pomogło Ci osiągnąć ten efekt?" if praise
                       else "Co w ostatnich tygodniach udało Ci się najbardziej?"]},
        {"topic": "F — Fakty", "legal_basis": "FUKO",
         "questions": [f"Opisuję sytuację: „{fact}”. Jak ją widzisz?" if fact
                       else "Opisz konkretną sytuację z ostatnich tygodni (co, kiedy, z jakim skutkiem)."]},
        {"topic": "U — Ustosunkowanie", "legal_basis": "FUKO",
         "questions": ["Jak ta sytuacja wpłynęła na Ciebie i na zespół?"]},
        {"topic": "K — Konsekwencje", "legal_basis": "FUKO",
         "questions": ["Jakie skutki dla realizacji zadań i współpracy widzisz, jeśli sytuacja się powtórzy lub utrwali?"]},
        {"topic": "O — Oczekiwania i kolejny krok", "legal_basis": "FUKO",
         "questions": ["Co konkretnie zrobimy do następnej rozmowy i jakiego wsparcia potrzebujesz?"]},
    ]
    return goal, agenda


def _mock_interview(stage: str, criteria: List[CriterionScore], notes: List[str]):
    names = [c.name for c in criteria]
    if stage == STAGE_FEEDBACK:
        goal, agenda = _mock_feedback(notes)
    elif stage == STAGE_INITIAL:
        goal = "Wspólne ustalenie obowiązków, kryteriów i oczekiwań na cały okres oceny oraz celów do osiągnięcia."
        agenda = [
            {"topic": "Główne obowiązki wynikające z opisu stanowiska pracy", "legal_basis": "§ 5 ust. 1 pkt 1",
             "questions": ["Które zadania z opisu stanowiska zajmują obecnie najwięcej czasu?",
                           "Czy opis stanowiska odpowiada faktycznie wykonywanym zadaniom? Co wymaga aktualizacji?"]},
            {"topic": "Kryteria obowiązkowe i wybrane kryteria dodatkowe", "legal_basis": "§ 5 ust. 1 pkt 2",
             "questions": ["Jak rozumiesz kryteria: " + ", ".join(names) + "?" if names else
                           "Które kryteria z katalogu najlepiej opisują kluczowe zadania na stanowisku?",
                           "Dlaczego wybrane kryteria dodatkowe są najistotniejsze dla tego stanowiska?"]},
            {"topic": "Oczekiwania co do sposobu spełniania kryteriów", "legal_basis": "§ 5 ust. 1 pkt 3",
             "questions": [f"Jak w codziennej pracy wygląda spełnianie kryterium „{n}” na poziomie oczekiwań?"
                           for n in names[:3]] or ["Jakie zachowania będą świadczyć o spełnianiu oczekiwań?"]},
            {"topic": "Cele do osiągnięcia w okresie oceny i sposób ich realizacji", "legal_basis": "§ 5 ust. 1 pkt 4",
             "questions": ["Jakie 2–3 cele są realne i mierzalne w perspektywie najbliższych 24 miesięcy?",
                           "Jakiego wsparcia potrzebujesz, aby je zrealizować, i jak będziemy monitorować postęp?"]},
        ]
    else:
        goal = "Omówienie sposobu wykonywania obowiązków i realizacji celów oraz ustalenie kierunków rozwoju przed sporządzeniem oceny."
        low = [c.name for c in criteria if c.score is not None and c.score <= 3]
        high = [c.name for c in criteria if c.score is not None and c.score >= 7]
        questions = ["Które zadania z okresu oceny uważasz za swoje największe osiągnięcia?"]
        questions += [f"Co wpłynęło na wyniki w kryterium „{n}” i czego potrzebujesz, aby je poprawić?" for n in low]
        questions += [f"Jak możemy wykorzystać Twoje mocne strony w kryterium „{n}” w zespole?" for n in high]
        agenda = [
            {"topic": "Główne obowiązki i sposób ich realizacji z uwzględnieniem kryteriów", "legal_basis": "§ 10 pkt 1",
             "questions": questions},
            {"topic": "Realizacja celów wyznaczonych na okres oceny", "legal_basis": "§ 10 pkt 2",
             "questions": ["Które z ustalonych celów udało się zrealizować, a które nie — i dlaczego?",
                           "Jakie okoliczności (zmiany zadań, nieobecności, zastępstwa) wpłynęły na realizację celów?"]},
            {"topic": "Kierunki dalszego rozwoju zawodowego i potrzeby szkoleniowe", "legal_basis": "§ 10 pkt 3",
             "questions": ["Jakie kompetencje chcesz rozwijać w kolejnym okresie?",
                           "Jakie formy rozwoju są dla Ciebie najskuteczniejsze (szkolenie, mentoring, nowe zadania)?"]},
        ]
    situations = [
        {"situation": "Oceniany nie zgadza się z oceną lub reaguje emocjonalnie",
         "tip": "Odwołuj się do konkretnych faktów i dat z notatek, oddziel ocenę zachowań od oceny osoby, daj czas na "
                "odpowiedź i poinformuj o prawie do sprzeciwu do dyrektora generalnego w 7 dni (art. 83 ust. 1)."},
        {"situation": "Oceniany ocenia swoje wyniki znacznie wyżej niż przełożony",
         "tip": "Zapytaj o przykłady, które to potwierdzają, i porównaj je z ustalonymi na początku oczekiwaniami."},
    ]
    if any(OVERLOAD_RE.search(n) for n in notes):
        situations.append({"situation": "W notatkach są sygnały przeciążenia (nadgodziny, zastępstwa)",
                           "tip": "Zapytaj o obciążenie i uwzględnij w ocenie okoliczności niezależne od ocenianego; "
                                  "zaproponuj zmianę priorytetów lub podział zadań."})
    return goal, agenda, situations


def _development_catalog(criteria: List[CriterionScore]) -> List[Dict]:
    """Działania rozwojowe do IPRZ — w pierwszej kolejności dla kryteriów z najniższymi ocenami.

    Każda pozycja ma zawsze sekcje 70-20-10 o koszcie 0 zł (małe urzędy bez budżetu szkoleniowego)."""
    ranked = sorted(criteria, key=lambda c: c.score if c.score is not None else 5)
    chosen = [c for c in ranked if c.score is None or c.score <= 5][:MAX_DEVELOPMENT_CRITERIA] or ranked[:2]
    entries = [{"criterion": c.name, "actions": DEVELOPMENT_ACTIONS[c.name], "estimatedCostPln": 0,
                **development_70_20_10(c.name)} for c in chosen if DEVELOPMENT_ACTIONS.get(c.name)]
    return entries or [{"criterion": GENERAL_DEVELOPMENT_AREA, "actions": [], "estimatedCostPln": 0,
                        **development_70_20_10(GENERAL_DEVELOPMENT_AREA)}]


# ---------------------------------------------------------------- karty trudnych rozmów (rozmowa oceniająca, § 10)

SCORE_DROP, NEGATIVE_EVALUATION, OVERLOAD_SIGNALS = "SCORE_DROP", "NEGATIVE_EVALUATION", "OVERLOAD_SIGNALS"
# Treść kart jest stała (nie z LLM) — granice roli nie mogą zostać „rozmyte” przez model.
DIFFICULT_SCENARIOS: Dict[str, Dict] = {
    SCORE_DROP: {
        "title": "Spadek wyników względem poprzedniej oceny",
        "structure": ["Otwarcie: cel rozmowy i docenienie tego, co działa",
                      "Fakty: porównanie z oczekiwaniami ustalonymi na rozmowie rozpoczynającej (daty, przykłady)",
                      "Perspektywa ocenianego: przyczyny zmiany i okoliczności (zmiana zadań, zastępstwa, nieobecności)",
                      "Plan: 1–3 konkretne działania w IPRZ i termin rozmowy kontrolnej"],
        "questions": ["Jak sam(a) oceniasz zmianę wyników od poprzedniego okresu oceny?",
                      "Co zmieniło się w Twoich zadaniach lub warunkach pracy w tym czasie?",
                      "Które oczekiwania były niejasne lub trudne do spełnienia?",
                      "Jakiego wsparcia ode mnie potrzebujesz, żeby wrócić do wcześniejszego poziomu?"],
        "role_boundaries": ["Rozmawiaj o zachowaniach i wynikach zadań, nie o cechach osoby.",
                            "Nie przesądzaj oceny przed wysłuchaniem ocenianego — ocenę sporządzasz po rozmowie."],
    },
    NEGATIVE_EVALUATION: {
        "title": "Zakomunikowanie oceny negatywnej",
        "structure": ["Otwarcie: spokojne przedstawienie celu rozmowy i wyniku oceny",
                      "Uzasadnienie: konkretne fakty z całego okresu oceny powiązane z kryteriami",
                      "Wysłuchanie: czas na reakcję i stanowisko ocenianego, bez przerywania",
                      "Pouczenie: prawo do sprzeciwu w terminie 7 dni od zapoznania się z oceną (art. 83 ust. 1)",
                      "Dalsze kroki: ponowna ocena po 6 miesiącach (art. 81 ust. 9) i wsparcie w IPRZ"],
        "questions": ["Jak odbierasz przedstawione fakty — czy są sytuacje, które widzisz inaczej?",
                      "Co według Ciebie najbardziej wpłynęło na wyniki w tym okresie?",
                      "Jakie wsparcie pomoże Ci w okresie do ponownej oceny?"],
        "role_boundaries": [
            "Spokojnie poinformuj o prawie do wniesienia sprzeciwu w terminie 7 dni od zapoznania się z oceną do "
            "dyrektora generalnego urzędu (w urzędzie bez dyrektora generalnego — do kierownika urzędu); "
            "nie zniechęcaj do skorzystania z tego prawa.",
            "Nie wiąż rozmowy z decyzjami kadrowymi, których nie podejmujesz w tej rozmowie.",
            "Oddziel ocenę wykonywania obowiązków od oceny osoby."],
    },
    OVERLOAD_SIGNALS: {
        "title": "Sygnały przeciążenia pracą",
        "structure": ["Otwarcie: troska o realizację zadań i warunki pracy, bez oceniania",
                      "Zadania: przegląd bieżących zadań, terminów i zastępstw",
                      "Priorytety: co przesunąć, przekazać lub uprościć",
                      "Ustalenia: zmiany organizacyjne i termin krótkiej rozmowy kontrolnej"],
        "questions": ["Które zadania zajmują obecnie najwięcej czasu?",
                      "Które terminy są najbardziej zagrożone i co możemy przesunąć?",
                      "Które zadania możemy przekazać innej osobie lub uprościć?",
                      "Jakie zmiany w organizacji pracy ułatwiłyby Ci realizację priorytetów?"],
        "role_boundaries": [
            "BEZWZGLĘDNIE nie stawiaj diagnoz medycznych ani psychologicznych i nie dopytuj o stan zdrowia.",
            "Skup się wyłącznie na zadaniach, priorytetach, terminach i organizacji pracy.",
            "Jeśli pracownik sam poruszy kwestie zdrowotne — wysłuchaj i wskaż dostępne formy wsparcia w urzędzie "
            "(komórka kadr, służba medycyny pracy), bez komentowania ich treści."],
    },
}


def _difficult_conversation(scenario: Optional[str]) -> Optional[Dict]:
    if not scenario:
        return None
    card = DIFFICULT_SCENARIOS[scenario]
    return {"scenario": scenario, "title": card["title"], "structure": list(card["structure"]),
            "questions": clean_list(card["questions"], strip_medical_content),
            "role_boundaries": list(card["role_boundaries"])}


# ---------------------------------------------------------------- kultura doceniania (SBI / FUKO)

APPRECIATION_SYSTEM = (
    "Jesteś asystentem przełożonego w służbie cywilnej. Na podstawie obserwacji piszesz krótki (2–3 zdania), "
    "konkretny komunikat doceniający metodą SBI: Sytuacja, Zachowanie, Wpływ. Bez ogólników, bez ocen punktowych, "
    "bez treści medycznych. Odpowiadasz wyłącznie JSON: {\"situation\": \"..\", \"behavior\": \"..\", "
    "\"impact\": \"..\", \"message\": \"..\"}. Zachowuj tokeny w nawiasach kwadratowych bez zmian."
)


def appreciation_nudge(llm: LLMClient, employee_name: str, observations: List[str], completed_tasks: List[str]) -> Dict:
    masker = PiiMasker(known_names=[employee_name] if employee_name else [])
    person = masker.mask(employee_name) if employee_name else "pracownik"
    masked_obs = [masker.mask(o) for o in observations]
    masked_tasks = [masker.mask(t) for t in completed_tasks]
    obs_lines = "\n".join(f"- {o}" for o in masked_obs) or "- (brak)"
    task_lines = "\n".join(f"- {t}" for t in masked_tasks) or "- (brak)"
    prompt = (f"Pracownik: {person}\nPozytywne obserwacje:\n{obs_lines}\nZamknięte trudne zadania:\n{task_lines}\n"
              "Przygotuj komunikat doceniający SBI do przekazania jeszcze przed oceną okresową.")

    answer = llm.chat_json(APPRECIATION_SYSTEM, prompt)
    if answer and all(str(answer.get(k, "")).strip() for k in ("situation", "behavior", "impact", "message")):
        mode, sbi = llm.mode, {k: str(answer[k]) for k in ("situation", "behavior", "impact", "message")}
    else:
        mode = MODE_MOCK if llm.mode == MODE_MOCK else MODE_FALLBACK
        sbi = _mock_appreciation(person, masked_obs, masked_tasks)

    clean = lambda s: unmask_pii(strip_medical_content(strip_score_proposals(s)), masker.mapping)  # noqa: E731
    return {
        "masked_prompt": prompt,
        "situation": clean(sbi["situation"]),
        "behavior": clean(sbi["behavior"]),
        "impact": clean(sbi["impact"]),
        "message": clean(sbi["message"]),
        "mode": mode,
        "complianceMeta": compliance_meta(masker.redacted_count),
    }


def _mock_appreciation(person: str, observations: List[str], tasks: List[str]) -> Dict[str, str]:
    source = (next((t for t in tasks if t.strip()), None)
              or next((o for o in observations if APPRECIATION_RE.search(o)), None)
              or next((o for o in observations if o.strip()), None))
    if not source:
        situation = "[Uzupełnij: kiedy i przy jakim zadaniu]"
        behavior = "[Uzupełnij: co konkretnie zrobił(a) pracownik]"
    else:
        situation = f"Przy zadaniu: {_snippet(source)}"
        behavior = "Twoje działanie opisane powyżej — konkretne i doprowadzone do końca."
    impact = "Dzięki temu zespół zrealizował zadanie w terminie, a odbiorcy otrzymali rzetelny wynik."
    message = (f"{person}, chcę Ci podziękować. {situation}. {behavior} {impact} "
               "To ważny wkład w pracę zespołu.")
    return {"situation": situation, "behavior": behavior, "impact": impact, "message": message}


# ---------------------------------------------------------------- parowanie mentoringowe w zespole

MAX_MENTEES_PER_MENTOR = 2


@dataclass
class MentoringMember:
    alias: str
    strong_criteria: List[str]
    development_areas: List[str]


def _criterion_name(value: str) -> str:
    value = " ".join(str(value).split())
    return CRITERIA.get(value.upper(), value)


def team_mentoring(members: List[MentoringMember]) -> Dict:
    """Deterministyczne parowanie: obszar rozwojowy osoby ↔ mocne kryterium innej osoby; mentor max 2 osoby."""
    masker = PiiMasker()
    team = [(m, masker.mask(m.alias), {_criterion_name(c).lower(): _criterion_name(c) for c in m.strong_criteria},
             [_criterion_name(c) for c in m.development_areas]) for m in members]
    prompt = "Macierz zespołu (aliasy):\n" + "\n".join(
        f"- {alias}: mocne={', '.join(strong.values()) or '-'}; rozwój={', '.join(areas) or '-'}"
        for _, alias, strong, areas in team)

    load: Dict[str, int] = {}
    pairs, unmatched = [], []
    for _, mentee, _, areas in team:
        for area in dict.fromkeys(areas):
            candidates = [alias for _, alias, strong, _ in team
                          if alias != mentee and area.lower() in strong and load.get(alias, 0) < MAX_MENTEES_PER_MENTOR]
            if not candidates:
                unmatched.append({"mentee": mentee, "criterion": area})
                continue
            mentor = min(candidates, key=lambda a: load.get(a, 0))
            load[mentor] = load.get(mentor, 0) + 1
            pairs.append({"mentor": mentor, "mentee": mentee, "criterion": area,
                          "rationale": f"Mocna strona mentora w kryterium „{area}” odpowiada obszarowi rozwojowemu "
                                       "osoby mentorowanej — mentoring wewnętrzny (20% w modelu 70-20-10, koszt 0 zł)."})

    unmask = lambda s: unmask_pii(s, masker.mapping)  # noqa: E731
    return {
        "masked_prompt": prompt,
        "pairs": [{**p, "mentor": unmask(p["mentor"]), "mentee": unmask(p["mentee"])} for p in pairs],
        "unmatched": [{**u, "mentee": unmask(u["mentee"])} for u in unmatched],
        "mode": "RULES",
        "complianceMeta": compliance_meta(masker.redacted_count),
    }
