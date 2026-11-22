"""Deterministyczna kontrola spójności arkusza (typowe błędy z przykładów KPRM). Nigdy nie modyfikuje tekstu.

Reguły zostały skalibrowane na oficjalnych arkuszach wzorcowych KPRM (tests/fixtures) — test regresji pilnuje,
by wykrywane były błędy celowo umieszczone w przykładach, bez fałszywych alarmów w poprawnych arkuszach.
"""
import re
from dataclasses import dataclass
from datetime import date, timedelta
from typing import Iterable, List, Optional

from .catalog import CRITERIA_NAMES
from .pii import PiiMasker

ERROR, WARNING = "ERROR", "WARNING"

# (kod, etykieta, górna granica średniej włącznie) — § 11 ust. 1 pkt 2
LEVELS = [
    ("SIGNIFICANTLY_BELOW", "znacznie poniżej oczekiwań", 2.0),
    ("BELOW", "poniżej oczekiwań", 4.0),
    ("MEETS", "na poziomie oczekiwań", 6.0),
    ("ABOVE", "powyżej oczekiwań", 8.0),
    ("SIGNIFICANTLY_ABOVE", "znacznie powyżej oczekiwań", 9.0),
]
_LABEL_BY_CODE = {code: label for code, label, _ in LEVELS}
_CODE_BY_LABEL = {label: code for code, label, _ in LEVELS}
LEVEL_PHRASE_RE = re.compile(r"(?:znacznie\s+)?(?:poniżej|powyżej)\s+oczekiwań|na\s+poziomie\s+oczekiwań",
                             re.IGNORECASE)
# Słowa wskazujące, że poziom dotyczy całej oceny, a nie pojedynczego kryterium.
OVERALL_HINT_RE = re.compile(r"końcow|ogóln|całoś|ocen[ęa] okresow|wykonywanie obowiązków", re.IGNORECASE)
LEVEL_CONTEXT_CHARS = 100
MIN_CRITERION_NAME_LEN = 4
AVERAGE_RE = re.compile(r"średni(?:a|ą|ej)\b[^\d\n.]{0,40}?(\d+(?:[,.]\d+)?)", re.IGNORECASE)
AVERAGE_TOLERANCE = 0.05

MIN_DUPLICATE_LEN = 30
_PARAGRAPH_SPLIT_RE = re.compile(r"\n\s*\n|\n")
_SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?])\s+")
_CLAUSE_BOUNDARY_RE = re.compile(r"[.!?;\n•]")

MALE_PAST_RE = re.compile(r"\b\w{2,}(?:ał|ił|ył|ął)\b")
FEMALE_PAST_RE = re.compile(r"\b\w{2,}(?:ała|iła|yła|ęła)\b")
NOT_VERBS = {"dział", "wydział", "udział", "przydział", "oddział", "podział", "rozdział", "materiał", "potencjał",
             "kanał", "sygnał", "kapitał", "ideał", "upał", "zapał", "zespół"}
MALE_WORDS = {"pan", "oceniany", "zaangażowany", "odpowiedzialny", "samodzielny", "sumienny", "kreatywny",
              "komunikatywny", "rzetelny", "zmotywowany", "dyspozycyjny", "urzędnik"}
FEMALE_WORDS = {"pani", "oceniana", "zaangażowana", "odpowiedzialna", "samodzielna", "sumienna", "kreatywna",
                "komunikatywna", "rzetelna", "zmotywowana", "dyspozycyjna", "urzędniczka", "pracownica"}
# Rzeczowniki nieosobowe, które bywają podmiotem czasownika w uzasadnieniu („zespół pracował”, „sprawa zostawała”).
NON_PERSON_SUBJECTS = {"zespół", "sprawa", "ocena", "praca", "sytuacja", "komórka", "jednostka", "wydział",
                       "departament", "system", "projekt", "atmosfera", "dokument", "wniosek", "procedura",
                       "incydent", "kontrola", "inicjatywa", "zmiana", "liczba", "część", "większość"}
NON_PERSON_SUFFIXES = ("ość", "cja", "cji", "nie")
SUBJECT_WINDOW_WORDS = 5
_WORD_RE = re.compile(r"\w+")

RECENCY_WINDOW_DAYS = 90
RECENCY_SHARE_THRESHOLD = 0.75
# przy 1–2 notatkach rozkład w czasie nic nie mówi — ostrzeżenie byłoby szumem
MIN_NOTES_FOR_RECENCY = 3


@dataclass
class Finding:
    code: str
    severity: str
    message: str


def level_for_average(average: float) -> str:
    for code, _, upper in LEVELS:
        if average <= upper + 1e-9:
            return code
    return LEVELS[-1][0]


def normalize_gender(gender: Optional[str]) -> Optional[str]:
    g = (gender or "").strip().upper()
    if g in {"F", "K", "FEMALE", "KOBIETA", "W"}:
        return "F"
    if g in {"M", "MALE", "MĘŻCZYZNA", "MEZCZYZNA"}:
        return "M"
    return None


def check(employee_name: str, employee_gender: Optional[str], criteria_part1: List[str], criteria_part2: List[str],
          average_score: float, level: Optional[str], justification: str,
          grade_request_text: Optional[str], note_dates: Optional[List[date]] = None,
          period_end: Optional[date] = None, recent_scope_change: bool = False) -> List[Finding]:
    texts = [t for t in (justification or "", grade_request_text or "") if t.strip()]
    criteria = {_norm(c) for c in [*CRITERIA_NAMES, *criteria_part1, *criteria_part2]}
    return (_criteria_mismatch(criteria_part1, criteria_part2)
            + _foreign_names(employee_name, texts)
            + _level_mismatch(average_score, level, texts, criteria)
            + _average_mismatch(average_score, texts)
            + _duplicates(justification or "", grade_request_text or "")
            + _gender_mismatch(employee_gender, texts)
            + _recency_bias(note_dates or [], period_end)
            + _scope_change_context(recent_scope_change))


def _norm(s: str) -> str:
    return re.sub(r"\s+", " ", s).strip().lower()


def _criteria_mismatch(part1: List[str], part2: List[str]) -> List[Finding]:
    a, b = {_norm(c) for c in part1}, {_norm(c) for c in part2}
    if a == b:
        return []
    details = []
    if a - b:
        details.append("brak w części II: " + ", ".join(sorted(a - b)))
    if b - a:
        details.append("brak w części I: " + ", ".join(sorted(b - a)))
    return [Finding("CRITERIA_MISMATCH", ERROR, "Kryteria w części I i II są różne (" + "; ".join(details) + ")")]


def _foreign_names(employee_name: str, texts: List[str]) -> List[Finding]:
    masker = PiiMasker(known_names=[employee_name])
    for t in texts:
        masker.mask(t)
    foreign = sorted({p.text for p in masker.detected_persons if not p.known})
    return [Finding("FOREIGN_NAME", ERROR,
                    f"W treści występuje inna osoba niż oceniany ({employee_name}): {name} — możliwy błąd kopiuj-wklej")
            for name in foreign]


def _clause_before(text: str, pos: int) -> str:
    """Fragment bieżącego zdania/punktu przed pozycją (ograniczony do LEVEL_CONTEXT_CHARS znaków)."""
    window = text[max(0, pos - LEVEL_CONTEXT_CHARS):pos]
    boundaries = list(_CLAUSE_BOUNDARY_RE.finditer(window))
    return window[boundaries[-1].end():] if boundaries else window


def _level_mismatch(average: float, level: Optional[str], texts: List[str], criteria: Iterable[str]) -> List[Finding]:
    expected = level_for_average(average)
    expected_label = _LABEL_BY_CODE[expected]
    warnings = []
    if level:
        given = level.strip().upper() if level.strip().upper() in _LABEL_BY_CODE else _CODE_BY_LABEL.get(_norm(level))
        if given and given != expected:
            warnings.append(Finding("LEVEL_MISMATCH", ERROR,
                                    f"Poziom „{_LABEL_BY_CODE[given]}” nie odpowiada średniej {_fmt(average)} "
                                    f"(oczekiwany: „{expected_label}”)"))
    reported = set()
    for text in texts:
        for m in LEVEL_PHRASE_RE.finditer(text):
            phrase = _norm(m.group())
            context = _clause_before(text, m.start())
            about_criterion = _mentions_criterion(context, criteria) or "ocena:" in context.lower()
            if about_criterion and not OVERALL_HINT_RE.search(context):
                continue  # poziom pojedynczego kryterium, np. „rzetelność – na poziomie poniżej oczekiwań”
            if _CODE_BY_LABEL.get(phrase) != expected and phrase not in reported:
                reported.add(phrase)
                warnings.append(Finding("LEVEL_MISMATCH", ERROR,
                                        f"Tekst mówi „{phrase}”, a średnia {_fmt(average)} oznacza „{expected_label}”"))
    return warnings


def _mentions_criterion(context: str, criteria: Iterable[str]) -> bool:
    text = _norm(context)
    return any(len(c) >= MIN_CRITERION_NAME_LEN and re.search(rf"\b{re.escape(c)}\b", text) for c in criteria)


def _average_mismatch(average: float, texts: List[str]) -> List[Finding]:
    """Średnia podana w tekście (także z przecinkiem dziesiętnym, np. „4,2”) musi zgadzać się z wyliczoną."""
    findings = []
    for text in texts:
        for m in AVERAGE_RE.finditer(text):
            stated = float(m.group(1).replace(",", "."))
            if abs(stated - average) > AVERAGE_TOLERANCE:
                findings.append(Finding("AVERAGE_MISMATCH", ERROR,
                                        f"W tekście podano średnią {m.group(1)}, a wyliczona średnia to {_fmt(average)}"))
    return findings


def _fmt(value: float) -> str:
    return f"{value:.1f}".replace(".", ",")


def _duplicates(justification: str, grade_text: str) -> List[Finding]:
    warnings = []
    if justification.strip() and _norm(justification) == _norm(grade_text):
        return [Finding("DUPLICATE_TEXT", WARNING, "Umotywowanie wniosku jest kopią uzasadnienia oceny")]
    seen, reported = {}, set()
    for source, text in (("uzasadnienie", justification), ("umotywowanie wniosku", grade_text)):
        for para in _PARAGRAPH_SPLIT_RE.split(text):
            for fragment in dict.fromkeys([para.strip()] + [f.strip() for f in _SENTENCE_SPLIT_RE.split(para)]):
                key = re.sub(r"[^\w ]", "", _norm(fragment))
                if len(key) < MIN_DUPLICATE_LEN or key in reported:
                    continue
                if key in seen:
                    reported.add(key)
                    where = source if seen[key] == source else f"{seen[key]} i {source}"
                    warnings.append(Finding("DUPLICATE_TEXT", WARNING,
                                            f"Powtórzony fragment ({where}): „{fragment.strip()[:80]}”"))
                else:
                    seen[key] = source
    return warnings


def _has_non_person_subject(text: str, verb_start: int) -> bool:
    context = _CLAUSE_BOUNDARY_RE.split(text[max(0, verb_start - 80):verb_start])[-1]
    words = [w.lower() for w in _WORD_RE.findall(context)][-SUBJECT_WINDOW_WORDS:]
    return any(w in NON_PERSON_SUBJECTS or (len(w) > 4 and w.endswith(NON_PERSON_SUFFIXES)) for w in words)


def _gendered_verbs(text: str, pattern: re.Pattern) -> set:
    return {m.group().lower() for m in pattern.finditer(text)
            if m.group().lower() not in NOT_VERBS and not _has_non_person_subject(text, m.start())}


def _gender_mismatch(gender: Optional[str], texts: List[str]) -> List[Finding]:
    g = normalize_gender(gender)
    if g is None:
        return []
    text = " ".join(texts)
    words = {w.lower() for w in _WORD_RE.findall(text)}
    if g == "F":
        wrong = sorted(_gendered_verbs(text, MALE_PAST_RE) | (words & MALE_WORDS))
        who = "oceniana jest kobietą"
    else:
        wrong = sorted(_gendered_verbs(text, FEMALE_PAST_RE) | (words & FEMALE_WORDS))
        who = "oceniany jest mężczyzną"
    if not wrong:
        return []
    return [Finding("GENDER_MISMATCH", WARNING,
                    f"Forma gramatyczna niezgodna z płcią ({who}): " + ", ".join(wrong[:8]))]


def _recency_bias(note_dates: List[date], period_end: Optional[date]) -> List[Finding]:
    """Efekt świeżości: ponad 75% notatek z ostatnich 90 dni okresu oceny (koniec okresu albo najpóźniejsza notatka)."""
    if len(note_dates) < MIN_NOTES_FOR_RECENCY:
        return []
    end = period_end or max(note_dates)
    window_start = end - timedelta(days=RECENCY_WINDOW_DAYS)
    recent = sum(1 for d in note_dates if window_start < d <= end)
    share = recent / len(note_dates)
    if share <= RECENCY_SHARE_THRESHOLD:
        return []
    return [Finding("RECENCY_BIAS", WARNING,
                    f"{round(share * 100)}% notatek ({recent} z {len(note_dates)}) pochodzi z ostatnich "
                    f"{RECENCY_WINDOW_DAYS} dni okresu oceny — ryzyko oceniania wyłącznie ostatnich zdarzeń (efekt "
                    "świeżości). Uzupełnij uzasadnienie o fakty z całego okresu oceny.")]


def _scope_change_context(recent_scope_change: bool) -> List[Finding]:
    if not recent_scope_change:
        return []
    return [Finding("RECENT_SCOPE_CHANGE", WARNING,
                    "W ostatnich 6 miesiącach zmienił się zakres obowiązków — uwzględnij w ocenie okres wdrożenia "
                    "w nowe zadania i oceniaj je względem oczekiwań ustalonych po zmianie (§ 9).")]
