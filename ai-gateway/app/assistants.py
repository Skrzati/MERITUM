"""Asystenci AI: sokratejski asystent uzasadnień i HR-partner. LLM widzi wyłącznie tekst po maskowaniu PII."""
import re
from dataclasses import dataclass
from typing import Dict, List, Optional

from .catalog import DEVELOPMENT_ACTIONS
from .guardrails import clean_list, strip_medical_content, strip_score_proposals
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
    criteria_lines = "\n".join(f"- {c.name}: {c.score if c.score is not None else 'brak'}" for c in criteria)
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
    signals_lines = "\n".join(f"- {k}: {v}" for k, v in sorted(signals.items())) or "- (brak)"
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

STAGE_INITIAL, STAGE_EVALUATION = "INITIAL", "EVALUATION"
MAX_DEVELOPMENT_CRITERIA = 4

INTERVIEW_SYSTEM = (
    "Jesteś asystentem przełożonego w służbie cywilnej. Przygotowujesz plan rozmowy w ramach oceny okresowej "
    "zgodnie z § 5 ust. 1 (rozmowa rozpoczynająca, 4 zagadnienia) albo § 10 (rozmowa oceniająca, 3 zagadnienia) "
    "rozporządzenia PRM z 4.04.2016. NIGDY nie proponujesz punktów ani oceny. Odpowiadasz wyłącznie JSON: "
    "{\"goal\": \"..\", \"agenda\": [{\"topic\": \"..\", \"legal_basis\": \"..\", \"questions\": [..]}], "
    "\"difficult_situations\": [{\"situation\": \"..\", \"tip\": \"..\"}]}. Zachowuj tokeny [..] bez zmian."
)


def interview_prep(llm: LLMClient, employee_name: str, sheet_type: str, stage: str, criteria: List[CriterionScore],
                   notes: List[str]) -> Dict:
    masker = PiiMasker(known_names=[employee_name])
    person = masker.mask(employee_name)
    masked_notes = [masker.mask(n) for n in notes]
    kind = "rozpoczynająca (§ 5 ust. 1)" if stage == STAGE_INITIAL else "oceniająca (§ 10)"
    criteria_lines = "\n".join(f"- {c.name}" + (f": {c.score}" if c.score is not None else "") for c in criteria)
    notes_lines = "\n".join(f"- {n}" for n in masked_notes) or "- (brak notatek)"
    prompt = (f"Rozmowa: {kind}\nOceniany: {person}\nArkusz: {sheet_type}\nKryteria:\n{criteria_lines or '- (brak)'}\n"
              f"Notatki z okresu oceny:\n{notes_lines}\nPrzygotuj cel rozmowy, agendę z pytaniami i wskazówki.")

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
        "mode": mode,
    }


def _mock_interview(stage: str, criteria: List[CriterionScore], notes: List[str]):
    names = [c.name for c in criteria]
    if stage == STAGE_INITIAL:
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
    """Działania rozwojowe do IPRZ — w pierwszej kolejności dla kryteriów z najniższymi ocenami."""
    ranked = sorted(criteria, key=lambda c: c.score if c.score is not None else 5)
    chosen = [c for c in ranked if c.score is None or c.score <= 5][:MAX_DEVELOPMENT_CRITERIA] or ranked[:2]
    return [{"criterion": c.name, "actions": DEVELOPMENT_ACTIONS.get(c.name, [])} for c in chosen
            if DEVELOPMENT_ACTIONS.get(c.name)]
