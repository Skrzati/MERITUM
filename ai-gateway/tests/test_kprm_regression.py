"""Test regresji na oficjalnych arkuszach wzorcowych KPRM (pakiet „Przykładowe wypełnione formularze oceny”).

Fixtures zawierają pola wyekstrahowane z arkuszy (dane fikcyjne z materiałów konkursowych). Oczekiwane kody to
błędy celowo umieszczone przez KPRM w przykładach; pozostałe arkusze muszą przejść bez fałszywych alarmów.
"""
import json
from pathlib import Path

import pytest

from app.consistency import check

FIXTURES = json.loads((Path(__file__).parent / "fixtures" / "kprm_example_sheets.json").read_text(encoding="utf-8"))


@pytest.mark.parametrize("sheet", FIXTURES, ids=[s["sheet"] for s in FIXTURES])
def test_detects_planted_errors_without_false_alarms(sheet):
    findings = check(sheet["employee_name"], sheet["employee_gender"], sheet["criteria_part1"],
                     sheet["criteria_part2"], sheet["average_score"], None, sheet["justification"],
                     sheet["grade_request_text"])
    assert sorted({f.code for f in findings}) == sorted(sheet["expected_codes"]), [f.message for f in findings]


def test_radca_copy_paste_names_previous_employee():
    sheet = next(s for s in FIXTURES if s["sheet"].startswith("5)"))
    messages = [f.message for f in check(sheet["employee_name"], "M", sheet["criteria_part1"], sheet["criteria_part2"],
                                         sheet["average_score"], None, sheet["justification"], sheet["grade_request_text"])]
    assert any("Adama Kowalika" in m for m in messages)
    assert any("znacznie powyżej oczekiwań" in m and "6,3" in m for m in messages)
