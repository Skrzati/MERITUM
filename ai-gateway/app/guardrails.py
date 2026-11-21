"""Filtry bezpieczeństwa dla treści generowanych przez AI."""
import re
from typing import List

AI_DISCLAIMER = "Wygenerowane przez AI — wymaga weryfikacji i akceptacji przełożonego (AI Act art. 50)"

_SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?])\s+")
_SCORE_PROPOSAL_RES = [
    re.compile(r"\b[13579]\s*(?:pkt|punkt\w*)", re.IGNORECASE),
    # „7/9”, „9 / 9” — ocena zapisana jako ułamek skali
    re.compile(r"(?<![\d/])[13579]\s*/\s*9(?![\d/])"),
    # „zasługuje na 9”, „oceń na 7”, „wystaw 5”, „daj mu dziewiątkę”
    re.compile(r"\b(?:zasług\w*|zasłuż\w*|oce[nń]\w*|wystaw\w*|przyzna\w*|da[jć]\w*|dostać|otrzyma\w*)\b[^.?!]{0,40}?"
               r"(?:\bna\s+)?(?<![\d,.])[13579](?![\d,.]\d)(?!\s*(?:dni|mies|lat|osób|proc|%))\b", re.IGNORECASE),
    re.compile(r"\b(?:jedynk|trójk|piątk|siódemk|dziewiątk)\w*", re.IGNORECASE),
    re.compile(r"\b(?:proponuj\w*|sugeruj\w*|rekomend\w*|należy\s+przyzna\w*|powin\w+\s+otrzyma\w*)\b"
               r"[^.?!]*\b(?:ocen\w*|punkt\w*|pkt|\d)", re.IGNORECASE),
]
_MEDICAL_RE = re.compile(
    r"\b(?:diagnoz\w*|depresj\w*|zaburze\w*|chorob\w*|leczeni\w*|terapi\w*|psychiatr\w*|bezsenn\w*|nerwic\w*|"
    r"lęk\w*|uzależni\w*|antydepres\w*|farmakoter\w*|samobój\w*|nadciśnieni\w*|cukrzyc\w*|migren\w*|"
    r"niepełnospraw\w*|ciąż\w*|nowotw\w*)",
    re.IGNORECASE)


def _filter_sentences(text: str, patterns: List[re.Pattern]) -> str:
    sentences = _SENTENCE_SPLIT_RE.split(text.strip()) if text else []
    return " ".join(s for s in sentences if not any(p.search(s) for p in patterns))


def strip_score_proposals(text: str) -> str:
    """AI nigdy nie proponuje punktów — zdania sugerujące ocenę są usuwane."""
    return _filter_sentences(text, _SCORE_PROPOSAL_RES)


def strip_medical_content(text: str) -> str:
    """HR-partner podaje wyłącznie sugestie wspierające, bez treści medycznych."""
    return _filter_sentences(text, [_MEDICAL_RE])


def clean_list(items: List[str], cleaner) -> List[str]:
    return [c for c in (cleaner(str(i)) for i in items) if c]
