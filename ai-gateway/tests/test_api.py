import pytest
from fastapi.testclient import TestClient

from app import main
from app.guardrails import strip_score_proposals
from app.llm import LLMClient


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(main, "llm", LLMClient(api_key=""))
    return TestClient(main.app)


def test_health_reports_mock_mode(client):
    assert client.get("/health").json()["llm_mode"] == "MOCK"


def test_socratic_draft_masks_pii_and_never_proposes_points(client):
    body = {
        "employee_name": "Katarzyna Wiśniewska", "sheet_type": "C",
        "criteria_scores": [{"name": "Rzetelność", "score": 1}, {"name": "Współpraca", "score": 7}],
        "notes": ["Katarzyna Wiśniewska przepracowała 22 nadgodziny w marcu (k.wisniewska@mc.gov.pl)."],
    }
    data = client.post("/api/ai/socratic-draft", json=body).json()

    assert "Wiśniewsk" not in data["masked_prompt"] and "@" not in data["masked_prompt"]
    assert "[OSOBA_1]" in data["masked_prompt"]
    assert data["socratic_questions"] and "AI Act art. 50" in data["ai_disclaimer"]
    assert "Katarzyna Wiśniewska" in data["draft_justification"]
    assert not any(c.isdigit() and "pkt" in q for q in data["socratic_questions"] for c in q)
    assert any("przeciążenie" in q for q in data["socratic_questions"])


def test_guardrail_removes_score_proposals():
    text = "Rzetelność była wysoka. Proponuję ocenę 7 w tym kryterium. Należy uzupełnić przykłady."
    assert strip_score_proposals(text) == "Rzetelność była wysoka. Należy uzupełnić przykłady."


def test_consistency_endpoint_never_modifies(client):
    body = {"employee_name": "Katarzyna Wiśniewska", "employee_gender": "K", "criteria_part1": ["A"],
            "criteria_part2": ["B"], "average_score": 6.3, "level": "ABOVE",
            "justification": "Jan Kowalski wykazał się wynikami znacznie powyżej oczekiwań."}
    data = client.post("/api/ai/consistency-check", json=body).json()

    assert data["auto_modified"] is False
    assert {"CRITERIA_MISMATCH", "FOREIGN_NAME", "LEVEL_MISMATCH", "GENDER_MISMATCH"} <= {w["code"] for w in data["warnings"]}


def test_hr_partner_detects_overload_without_medical_content(client):
    body = {"notes": ["Przejęła obowiązki nieobecnego członka zespołu (L4, 6 tygodni). Ryzyko przeciążenia.",
                      "Terminy dotrzymane mimo nadgodzin przy wdrożeniu KSeF."],
            "workload_signals": {"overtime_hours": 22, "concurrent_projects": 3}}
    data = client.post("/api/ai/hr-partner", json=body).json()

    assert data["burnout_risk_signal"] is True
    assert data["conversation_starters"] and data["organizational_suggestions"]
    assert "Terminy dotrzymane" in data["appreciation_moment"]
    assert "diagnoz" not in str(data).lower()
