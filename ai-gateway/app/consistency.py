"""Deterministyczna kontrola spójności arkusza (typowe błędy z przykładów KPRM). Nigdy nie modyfikuje tekstu."""
import re
from dataclasses import dataclass
from typing import List, Optional

from .pii import PiiMasker

ERROR, WARNING = "ERROR", "WARNING"

# (kod, etykieta, górna granica średniej włącznie) — § 11
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

MIN_DUPLICATE_LEN = 30
_PARAGRAPH_SPLIT_RE = re.compile(r"\n\s*\n|\n")
_SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?])\s+")

MALE_PAST_RE = re.compile(r"\b\w{2,}(?:ał|ił|ył|ął)\b")
FEMALE_PAST_RE = re.compile(r"\b\w{2,}(?:ała|iła|yła|ęła)\b")
NOT_VERBS = {"dział", "wydział", "udział", "przydział", "oddział", "podział", "rozdział", "materiał", "potencjał",
             "kanał", "sygnał", "kapitał", "ideał", "upał", "zapał", "zespół"}
MALE_WORDS = {"pan", "zaangażowany", "odpowiedzialny", "samodzielny", "sumienny", "kreatywny", "komunikatywny",
              "rzetelny", "zmotywowany", "dyspozycyjny", "urzędnik"}
FEMALE_WORDS = {"pani", "zaangażowana", "odpowiedzialna", "samodzielna", "sumienna", "kreatywna", "komunikatywna",
                "rzetelna", "zmotywowana", "dyspozycyjna", "urzędniczka", "pracownica"}
_WORD_RE = re.compile(r"\w+")


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
          grade_request_text: Optional[str]) -> List[Finding]:
    texts = [t for t in (justification or "", grade_request_text or "") if t.strip()]
    return (_criteria_mismatch(criteria_part1, criteria_part2)
            + _foreign_names(employee_name, texts)
            + _level_mismatch(average_score, level, texts)
            + _duplicates(justification or "", grade_request_text or "")
            + _gender_mismatch(employee_gender, texts))


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


def _level_mismatch(average: float, level: Optional[str], texts: List[str]) -> List[Finding]:
    expected = level_for_average(average)
    expected_label = _LABEL_BY_CODE[expected]
    warnings = []
    if level:
        given = level.strip().upper() if level.strip().upper() in _LABEL_BY_CODE else _CODE_BY_LABEL.get(_norm(level))
        if given and given != expected:
            warnings.append(Finding("LEVEL_MISMATCH", ERROR,
                                     f"Poziom „{_LABEL_BY_CODE[given]}” nie odpowiada średniej {average:.1f} "
                                     f"(oczekiwany: „{expected_label}”)"))
    for phrase in {_norm(m.group()) for t in texts for m in LEVEL_PHRASE_RE.finditer(t)}:
        if _CODE_BY_LABEL.get(phrase) != expected:
            warnings.append(Finding("LEVEL_MISMATCH", ERROR,
                                     f"Tekst mówi „{phrase}”, a średnia {average:.1f} oznacza „{expected_label}”"))
    return warnings


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


def _gender_mismatch(gender: Optional[str], texts: List[str]) -> List[Finding]:
    g = normalize_gender(gender)
    if g is None:
        return []
    text = " ".join(texts)
    words = {w.lower() for w in _WORD_RE.findall(text)}
    if g == "F":
        wrong = sorted({w.lower() for w in MALE_PAST_RE.findall(text)} - NOT_VERBS | (words & MALE_WORDS))
        who = "oceniana jest kobietą"
    else:
        wrong = sorted({w.lower() for w in FEMALE_PAST_RE.findall(text)} | (words & FEMALE_WORDS))
        who = "oceniany jest mężczyzną"
    if not wrong:
        return []
    return [Finding("GENDER_MISMATCH", WARNING,
                     f"Forma gramatyczna niezgodna z płcią ({who}): " + ", ".join(wrong[:8]))]
