"""Maskowanie danych osobowych przed wysłaniem tekstu do LLM.

Tokeny: [OSOBA_n], [EMAIL_n], [PESEL_n], [TELEFON_n], [KOMORKA_n] — numerowane, aby unmask_pii był jednoznaczny.
Ta sama osoba (również w formie odmienionej i pisana WERSALIKAMI) dostaje zawsze ten sam token.
Koszt liniowy względem długości tekstu — wyrażenia nie mogą wracać po długich ciągach znaków (ochrona przed DoS).
"""
import bisect
import re
from dataclasses import dataclass, field
from functools import lru_cache
from typing import Dict, Iterable, List, Optional, Tuple

UPPER = "A-ZĄĆĘŁŃÓŚŹŻ"
LOWER = "a-ząćęłńóśźż"

# Lookbehind: dopasowanie startuje tylko na początku ciągu znaków adresu — bez tego długi ciąg \w bez „@”
# był skanowany od każdej pozycji (koszt kwadratowy).
EMAIL_RE = re.compile(r"(?<![\w.+-])[\w.+-]{1,64}@[\w-]{1,63}(?:\.[\w-]{1,63}){1,8}")
PESEL_RE = re.compile(r"(?<![\d-])\d{2}[ -]?\d{2}[ -]?\d{2}[ -]?\d{5}(?![\d-])")
PHONE_RE = re.compile(r"(?<![\d+])(?:(?:\+|00)48[ -]?)?(?:\d{3}[ -]?\d{3}[ -]?\d{3}|\d{2}[ -]\d{3}[ -]\d{2}[ -]\d{2})(?![\d-])")
# Słowo z wielkiej litery albo WERSALIKAMI (min. 2 litery), opcjonalnie dwuczłonowe z łącznikiem.
CAP_WORD_RE = re.compile(rf"[{UPPER}][{LOWER}]+(?:-[{UPPER}][{LOWER}]+)?|[{UPPER}]{{2,}}(?:-[{UPPER}]{{2,}})?(?![{LOWER}])")
# „z kierownikiem Nowakiem” — nazwisko po rzeczowniku funkcyjnym pisanym małą literą.
FUNCTION_WORD_RE = re.compile(
    r"\b(?:kierowni|naczelni|dyrektor|zastępc|specjalist|inspektor|radc|referent|koordynator|pracowni|kolega|koleg|"
    r"koleżank|przełożon|podwładn|asystent|sekretar|ekspert|analityk|doradc|kadrow|referendarz)\w*\s+$")
FUNCTION_WORD_WINDOW = 30
# Nazwy instytucji i komórek po rzeczowniku funkcyjnym nie są nazwiskami („naczelnik Wydziału”).
INSTITUTION_PREFIXES = ("Departament", "Wydzia", "Biur", "Zesp", "Sekcj", "Referat", "Gabinet", "Ministerst", "Urz",
                        "Kancelari", "Służb", "Komend", "Inspekcj", "Izb", "Agencj", "Instytut", "Centr", "Generaln",
                        "Prezes", "Rad", "Sąd", "Policj", "Państw", "Krajow", "Wojew", "Powiat", "Gmin")
UNIT_RE = re.compile(
    rf"\b(?:Departament\w*|Wydzia\w+|Biur\w+|Zesp[oó]\w+|Sekcj\w+|Referat\w*|Gabinet\w*)"
    rf"(?:\s+ds\.)?(?:\s+(?:(?:i|oraz)\s+)?[{UPPER}][\w-]*)+"
)

HONORIFICS = {"Pan", "Pani", "Pana", "Panu", "Panią", "Panem", "Panie"}
# Tytuły/stanowiska po „Pan/Pani” nie są nazwiskami („Pan Dyrektor Przykład” -> osoba = Przykład).
TITLES = {
    f"{base}{suffix}"
    for base in ("Dyrektor", "Naczelnik", "Kierownik", "Prezes", "Inspektor", "Specjalist", "Radc", "Minister",
                 "Wojewod", "Starost", "Komendant", "Doktor", "Profesor", "Mecenas", "Sędzi", "Sekretarz", "Zastępc")
    for suffix in ("", "a", "e", "em", "owi", "ze", "y", "ą", "o", "u")
}
_TITLES_RE = re.compile(r"\b(?:" + "|".join(sorted(TITLES, key=len, reverse=True)) + r")\b")
MAX_TITLE_GAP = 30
FIRST_NAMES = {
    "Anna", "Maria", "Katarzyna", "Małgorzata", "Agnieszka", "Barbara", "Ewa", "Krystyna", "Elżbieta",
    "Magdalena", "Joanna", "Zofia", "Monika", "Aleksandra", "Natalia", "Karolina", "Marta", "Beata", "Dorota",
    "Jolanta", "Iwona", "Halina", "Danuta", "Teresa", "Justyna", "Paulina", "Julia", "Weronika", "Alicja",
    "Hanna", "Izabela", "Renata", "Sylwia", "Agata", "Patrycja", "Emilia", "Kinga", "Lena", "Wiktoria",
    "Piotr", "Krzysztof", "Andrzej", "Tomasz", "Paweł", "Jan", "Michał", "Marcin", "Jakub", "Adam", "Marek",
    "Łukasz", "Grzegorz", "Mateusz", "Wojciech", "Mariusz", "Dariusz", "Zbigniew", "Jerzy", "Maciej", "Rafał",
    "Robert", "Kamil", "Jacek", "Józef", "Ryszard", "Tadeusz", "Stanisław", "Kacper", "Bartosz", "Sebastian",
    "Szymon", "Filip", "Damian", "Dawid", "Przemysław", "Artur", "Sławomir", "Janusz", "Roman", "Antoni",
    "Wiktor", "Oskar", "Ewelina", "Kamila", "Marzena", "Grażyna", "Bożena", "Urszula", "Wanda", "Edyta",
}
# Końcówki fleksyjne imion i nazwisk po odcięciu rdzenia (po _fold: ą/ę -> e, ó -> o).
DECLENSION_ENDINGS = {
    "", "a", "e", "i", "y", "u", "o", "em", "om", "ie", "owi", "ow", "owie", "ami", "ach", "iem", "iej", "iego",
    "iemu", "im", "ich", "ej", "ego", "emu", "ym", "ych", "ze", "ce", "la", "lem", "lowi", "le", "ka", "kiem", "kowi",
    "ku", "ła", "łem", "łowi",
}
# Imiona 3-literowe mają rdzeń 2–3 liter — tylko końcówki ich odmiany (Jana, Janem, Janowi; Ewy, Ewie, Ewą/Ewę),
# żeby nie łapać przypadkowych słów zaczynających się od tego rdzenia.
SHORT_NAME_LEN = 3
SHORT_NAME_ENDINGS = {"", "a", "y", "e", "ie", "o", "u", "em", "om", "owi"}


def _fold(word: str) -> str:
    """Wyrównanie oboczności w odmianie nazwisk: ó/o (Wzór -> Wzora), ą/ę (Dąb -> Dęba)."""
    return word.lower().replace("ó", "o").replace("ą", "e").replace("ę", "e")


def _stem(word: str) -> str:
    """Prosty rdzeń do dopasowania form odmienionych (Kowalska -> kowalsk, Paweł -> paw, Marek -> mar)."""
    w = _fold(word)
    if len(w) >= 4 and (w.endswith("eł") or w.endswith("ek")):
        return w[:-2]
    if len(w) >= 3 and w[-1] in "aeiouyąę":
        return w[:-1]
    return w


def _inflects(word: str, base: str, endings=DECLENSION_ENDINGS) -> bool:
    """Czy słowo jest formą odmienioną imienia/nazwiska (Kowal -> Kowala, ale nie Kowalika)."""
    stem, w = _stem(base), _fold(word)
    return w.startswith(stem) and w[len(stem):] in endings


def _norm(word: str) -> str:
    """WERSALIKI → forma kanoniczna („KOWALSKA-NOWAK” → „Kowalska-Nowak”); pozostałe słowa bez zmian."""
    return "-".join(p.capitalize() for p in word.split("-")) if word.isupper() else word


_NAME_STEMS = [(_stem(n), SHORT_NAME_ENDINGS if len(n) <= SHORT_NAME_LEN else DECLENSION_ENDINGS) for n in FIRST_NAMES]


@lru_cache(maxsize=8192)
def is_first_name(word: str) -> bool:
    word = _norm(word)
    if "-" in word:  # „Anna-Maria” — imię podwójne
        return any(is_first_name(part) for part in word.split("-"))
    if word in FIRST_NAMES:
        return True
    w = _fold(word)
    return any(w.startswith(stem) and w[len(stem):] in endings for stem, endings in _NAME_STEMS)


@dataclass
class _Span:
    start: int
    end: int
    kind: str
    key: str
    original: str


@dataclass
class DetectedPerson:
    text: str
    known: bool


@dataclass
class PiiMasker:
    """Stanowy masker — wiele fragmentów tekstu dzieli wspólne tokeny i mapowanie."""
    known_names: List[str] = field(default_factory=list)
    known_units: List[str] = field(default_factory=list)
    mapping: Dict[str, str] = field(default_factory=dict)
    detected_persons: List[DetectedPerson] = field(default_factory=list)
    _tokens: Dict[Tuple[str, str], str] = field(default_factory=dict)
    _counters: Dict[str, int] = field(default_factory=dict)

    def __post_init__(self) -> None:
        for name in self.known_names:
            if name and name.strip():
                self._token("OSOBA", self._known_key(name.strip()), name.strip())

    def mask(self, text: str) -> str:
        if not text:
            return text
        spans = self._fixed_spans(text)
        spans += self._person_spans(text, spans)
        parts, pos = [], 0
        for s in self._non_overlapping(spans):
            parts.append(text[pos:s.start])
            parts.append(self._token(s.kind, s.key, s.original))
            pos = s.end
        parts.append(text[pos:])
        return "".join(parts)

    def _token(self, kind: str, key: str, original: str) -> str:
        if (kind, key) not in self._tokens:
            self._counters[kind] = self._counters.get(kind, 0) + 1
            token = f"[{kind}_{self._counters[kind]}]"
            self._tokens[(kind, key)] = token
            self.mapping[token] = original
        return self._tokens[(kind, key)]

    def _fixed_spans(self, text: str) -> List[_Span]:
        spans = [_Span(m.start(), m.end(), "EMAIL", m.group().lower(), m.group()) for m in EMAIL_RE.finditer(text)]
        spans += [_Span(m.start(), m.end(), "PESEL", re.sub(r"\D", "", m.group()), m.group()) for m in PESEL_RE.finditer(text)]
        spans += [_Span(m.start(), m.end(), "TELEFON", re.sub(r"\D", "", m.group())[-9:], m.group())
                  for m in PHONE_RE.finditer(text)]
        for unit in self.known_units:
            for m in re.finditer(re.escape(unit), text, re.IGNORECASE):
                spans.append(_Span(m.start(), m.end(), "KOMORKA", unit.lower(), unit))
        spans += [_Span(m.start(), m.end(), "KOMORKA", m.group().lower(), m.group()) for m in UNIT_RE.finditer(text)]
        return spans

    def _person_spans(self, text: str, taken: List[_Span]) -> List[_Span]:
        covered = _Coverage(taken)
        words = [m for m in CAP_WORD_RE.finditer(text) if _norm(m.group()) not in TITLES and not covered.contains(m.start())]
        spans: List[_Span] = []
        i = 0
        while i < len(words):
            w = words[i]
            nxt = words[i + 1] if i + 1 < len(words) and _adjacent(text, w, words[i + 1]) else None
            known = self._known_owner(w.group())
            if known:
                end = w.end()
                if nxt and self._known_owner(nxt.group()) == known:
                    end, i = nxt.end(), i + 1
                spans.append(_Span(w.start(), end, "OSOBA", known, self.mapping_name(known)))
                self.detected_persons.append(DetectedPerson(text[w.start():end], True))
            elif _norm(w.group()) in HONORIFICS and nxt and not self._known_owner(nxt.group()):
                # „Pana Adama Kowalika” — po zwrocie grzecznościowym imię i nazwisko albo samo nazwisko
                after = words[i + 2] if i + 2 < len(words) and _adjacent(text, nxt, words[i + 2]) else None
                if after and is_first_name(nxt.group()) and not self._known_owner(after.group()):
                    spans.append(self._auto_person(text, nxt, after))
                    i += 2
                else:
                    spans.append(self._auto_person(text, nxt, nxt))
                    i += 1
            elif is_first_name(w.group()):
                last = nxt if nxt and _norm(nxt.group()) not in HONORIFICS and not self._known_owner(nxt.group()) else None
                spans.append(self._auto_person(text, w, last or w))
                if last:
                    i += 1
            elif self._after_function_word(text, w):
                # tylko maskowanie — bez zgłaszania jako „inna osoba” (kontrola spójności zachowuje 0 fałszywych alarmów)
                spans.append(self._auto_person(text, w, w, report=False))
            i += 1
        return spans

    @staticmethod
    def _after_function_word(text: str, word) -> bool:
        if word.group().isupper() or _norm(word.group()).startswith(INSTITUTION_PREFIXES):
            return False
        return FUNCTION_WORD_RE.search(text[max(0, word.start() - FUNCTION_WORD_WINDOW):word.start()]) is not None

    def _auto_person(self, text: str, first, last, report: bool = True) -> _Span:
        original = text[first.start():last.end()]
        if report:
            self.detected_persons.append(DetectedPerson(original, False))
        return _Span(first.start(), last.end(), "OSOBA", "auto:" + _stem(_norm(last.group())), original)

    def _known_owner(self, word: str) -> Optional[str]:
        word = _norm(word)
        for name in self.known_names:
            parts = name.split()
            for idx, part in enumerate(parts):
                if len(part) < 3 or not _inflects(word, part):
                    continue
                # inne imię mianownikowe (np. Marta vs Marek) nie jest tą samą osobą
                is_first = idx == 0 and len(parts) > 1
                if is_first and word in FIRST_NAMES and word != part:
                    continue
                return self._known_key(name)
        return None

    def mapping_name(self, key: str) -> str:
        return self.mapping[self._tokens[("OSOBA", key)]]

    @staticmethod
    def _known_key(name: str) -> str:
        return "known:" + name.lower()

    @staticmethod
    def _non_overlapping(spans: Iterable[_Span]) -> List[_Span]:
        result: List[_Span] = []
        for s in sorted(spans, key=lambda s: (s.start, -(s.end - s.start))):
            if not result or s.start >= result[-1].end:
                result.append(s)
        return result


class _Coverage:
    """Czy pozycja leży w którymś z zajętych przedziałów — wyszukiwanie binarne zamiast przeglądu wszystkich."""

    def __init__(self, spans: Iterable[_Span]):
        ordered = sorted((s.start, s.end) for s in spans)
        self._starts = [start for start, _ in ordered]
        self._max_end, best = [], -1
        for _, end in ordered:
            best = max(best, end)
            self._max_end.append(best)

    def contains(self, pos: int) -> bool:
        idx = bisect.bisect_right(self._starts, pos) - 1
        return idx >= 0 and pos < self._max_end[idx]


def _adjacent(text: str, a, b) -> bool:
    """Słowa sąsiadują, jeśli dzieli je tylko spacja (lub tytuł, np. „Pan Dyrektor Kowalski”)."""
    gap = text[a.end():b.start()]
    if 0 < len(gap) <= 2 and gap.strip() == "":
        return True
    return 0 < len(gap) <= MAX_TITLE_GAP and _TITLES_RE.sub("", gap).strip() == "" and _TITLES_RE.search(gap) is not None


@dataclass
class MaskResult:
    masked: str
    mapping: Dict[str, str]


def mask_pii(text: str, known_names: Optional[List[str]] = None,
             known_units: Optional[List[str]] = None) -> MaskResult:
    masker = PiiMasker(known_names or [], known_units or [])
    return MaskResult(masker.mask(text), dict(masker.mapping))


def unmask_pii(text: str, mapping: Dict[str, str]) -> str:
    for token in sorted(mapping, key=len, reverse=True):
        text = text.replace(token, mapping[token])
    return text
