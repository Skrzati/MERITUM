import pytest
from fastapi.testclient import TestClient

from app import criteria_suggest, main
from app.catalog import CRITERIA
from app.llm import MODE_FALLBACK, LLMClient

MANDATORY_C = ["K04", "K05", "K08", "K10", "K11"]
BODY = {
    "position_title": "Główny specjalista", "department": "Departament Analiz Ekonomicznych", "sheet_type": "C",
    "mandatory_codes": MANDATORY_C,
    "responsibilities_summary": "Przygotowuje analizy danych i raporty, które zatwierdza Jan Kowalski (j.kowalski@kprm.gov.pl); "
                                "prezentuje wyniki na konferencjach, negocjuje umowy z kontrahentami.",
}


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(main, "llm", LLMClient(api_key="", live=False))
    return TestClient(main.app)


def test_suggests_max_three_catalog_criteria_outside_mandatory(client):
    data = client.post("/api/ai/suggest-criteria", json=BODY).json()

    codes = [s["code"] for s in data["suggestions"]]
    assert 0 < len(codes) <= 3 and len(set(codes)) == len(codes)
    assert all(c in CRITERIA and c not in MANDATORY_C for c in codes)
    assert codes[0] == "K09"
    assert all(len(s["expectations"]) == 2 and s["reason"] and s["name"] == CRITERIA[s["code"]]
               for s in data["suggestions"])
    assert data["mode"] == "MOCK" and "AI Act art. 50" in data["ai_disclaimer"]


def test_masks_pii_in_prompt(client):
    prompt = client.post("/api/ai/suggest-criteria", json=BODY).json()["masked_prompt"]

    assert "Kowalski" not in prompt and "@" not in prompt and "Analiz Ekonomicznych" not in prompt
    assert "[OSOBA_1]" in prompt


def test_llm_answer_filtered_to_catalog_and_non_mandatory(monkeypatch):
    class FakeLLM:
        mode = "LIVE"

        def chat_json(self, system, user):
            return {"suggestions": [{"code": "K08", "reason": "obowiązkowe"}, {"code": "K99", "reason": "spoza"},
                                    {"code": "k12", "reason": "Wystąpienia. Proponuję ocenę 7.",
                                     "expectations": ["Prezentacja wyników"]},
                                    {"code": "K12", "reason": "duplikat"}]}

    data = criteria_suggest.suggest_criteria(FakeLLM(), "Specjalista", "", "C", MANDATORY_C, "")

    assert [s["code"] for s in data["suggestions"]] == ["K12"]
    assert data["mode"] == "LIVE" and "7" not in data["suggestions"][0]["reason"]


def test_invalid_llm_answer_falls_back(monkeypatch):
    class BrokenLLM:
        mode = "LIVE"

        def chat_json(self, system, user):
            return {"suggestions": [{"code": "K04"}]}

    data = criteria_suggest.suggest_criteria(BrokenLLM(), "Specjalista", "", "C", MANDATORY_C, "")

    assert data["mode"] == MODE_FALLBACK and len(data["suggestions"]) == 3


def test_job_description_from_profile_is_masked_and_used(client):
    body = {**BODY, "responsibilities_summary": "",
            "job_description": "Opis stanowiska: prowadzi negocjacje z kontrahentami; kontakt: anna.nowak@kprm.gov.pl"}
    data = client.post("/api/ai/suggest-criteria", json=body).json()

    assert "Opis stanowiska pracy (art. 84" in data["masked_prompt"]
    assert "anna.nowak@kprm.gov.pl" not in data["masked_prompt"]
    assert data["suggestions"], "opis stanowiska wystarcza do podpowiedzi w trybie MOCK"
