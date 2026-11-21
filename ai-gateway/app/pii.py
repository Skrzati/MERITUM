"""Maskowanie danych osobowych przed wysłaniem tekstu do LLM.

Tokeny: [OSOBA_n], [EMAIL_n], [PESEL_n], [KOMORKA_n] — numerowane, aby unmask_pii był jednoznaczny.
Ta sama osoba (również w formie odmienionej) dostaje zawsze ten sam token.
"""
import re
from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Tuple

UPPER = "A-ZĄĆĘŁŃÓŚŹŻ"
LOWER = "a-ząćęłńóśźż"

EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
PESEL_RE = re.compile(r"(?<!\d)\d{11}(?!\d)")
CAP_WORD_RE = re.compile(rf"[{UPPER}][{LOWER}]+(?:-[{UPPER}][{LOWER}]+)?")
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


def is_first_name(word: str) -> bool:
    return word in FIRST_NAMES or any(
        _inflects(word, n, SHORT_NAME_ENDINGS if len(n) <= SHORT_NAME_LEN else DECLENSION_ENDINGS) for n in FIRST_NAMES)


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
        out = text
        for s in sorted(self._non_overlapping(spans), key=lambda s: s.start, reverse=True):
            out = out[:s.start] + self._token(s.kind, s.key, s.original) + out[s.end:]
        return out

    def _token(self, kind: str, key: str, original: str) -> str:
        if (kind, key) not in self._tokens:
            self._counters[kind] = self._counters.get(kind, 0) + 1
            token = f"[{kind}_{self._counters[kind]}]"
            self._tokens[(kind, key)] = token
            self.mapping[token] = original
        return self._tokens[(kind, key)]

    def _fixed_spans(self, text: str) -> List[_Span]:
        spans = [_Span(m.start(), m.end(), "EMAIL", m.group().lower(), m.group()) for m in EMAIL_RE.finditer(text)]
        spans += [_Span(m.start(), m.end(), "PESEL", m.group(), m.group()) for m in PESEL_RE.finditer(text)]
        for unit in self.known_units:
            for m in re.finditer(re.escape(unit), text, re.IGNORECASE):
                spans.append(_Span(m.start(), m.end(), "KOMORKA", unit.lower(), unit))
        spans += [_Span(m.start(), m.end(), "KOMORKA", m.group().lower(), m.group()) for m in UNIT_RE.finditer(text)]
        return spans

    def _person_spans(self, text: str, taken: List[_Span]) -> List[_Span]:
        words = [m for m in CAP_WORD_RE.finditer(text)
                 if m.group() not in TITLES and not any(t.start <= m.start() < t.end for t in taken)]
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
            elif w.group() in HONORIFICS and nxt and not self._known_owner(nxt.group()):
                # „Pana Adama Kowalika” — po zwrocie grzecznościowym imię i nazwisko albo samo nazwisko
                after = words[i + 2] if i + 2 < len(words) and _adjacent(text, nxt, words[i + 2]) else None
                if after and is_first_name(nxt.group()) and not self._known_owner(after.group()):
                    spans.append(self._auto_person(text, nxt, after))
                    i += 2
                else:
                    spans.append(self._auto_person(text, nxt, nxt))
                    i += 1
            elif is_first_name(w.group()):
                last = nxt if nxt and nxt.group() not in HONORIFICS and not self._known_owner(nxt.group()) else None
                spans.append(self._auto_person(text, w, last or w))
                if last:
                    i += 1
            i += 1
        return spans

    def _auto_person(self, text: str, first, last) -> _Span:
        original = text[first.start():last.end()]
        self.detected_persons.append(DetectedPerson(original, False))
        return _Span(first.start(), last.end(), "OSOBA", "auto:" + _stem(last.group()), original)

    def _known_owner(self, word: str) -> Optional[str]:
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
