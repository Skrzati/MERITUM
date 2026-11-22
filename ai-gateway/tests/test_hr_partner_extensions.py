"""Efekt świeżości, kultura doceniania, karty trudnych rozmów, IPRZ 70-20-10, mentoring i complianceMeta (AI Act)."""
import pytest
from fastapi.testclient import TestClient

from app import main
from app.guardrails import COMPLIANCE_DISCLAIMER
from app.llm import LLMClient
from app.pii import PiiMasker


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(main, "llm", LLMClient(api_key="", live=False))
    return TestClient(main.app)


CONSISTENT_SHEET = {"employee_name": "Anna Nowak", "criteria_part1": ["Rzetelność"], "criteria_part2": ["Rzetelność"],
                    "average_score": 5.0, "justification": "Zadania realizowane terminowo."}


def _codes(data):
    return {w["code"] for w in data["warnings"]}


def test_recency_bias_when_most_notes_come_from_last_90_days(client):
    notes = [{"date": d, "text": "obserwacja"} for d in ("2025-02-10", "2026-10-01", "2026-10-20", "2026-11-05",
                                                        "2026-11-15")]
    data = client.post("/api/ai/consistency-check",
                       json={**CONSISTENT_SHEET, "notes": notes, "periodEnd": "2026-11-30"}).json()

    warning = next(w for w in data["warnings"] if w["code"] == "RECENCY_BIAS")
    assert "80%" in warning["message"] and "ostatnich zdarzeń" in warning["message"]


def test_no_recency_bias_for_notes_spread_over_period(client):
    notes = [{"date": d} for d in ("2025-03-01", "2025-09-01", "2026-03-01", "2026-11-01")]
    data = client.post("/api/ai/consistency-check",
                       json={**CONSISTENT_SHEET, "notes": notes, "periodEnd": "2026-11-30"}).json()

    assert "RECENCY_BIAS" not in _codes(data)


def test_no_recency_bias_for_too_few_notes(client):
    data = client.post("/api/ai/consistency-check",
                       json={**CONSISTENT_SHEET, "notes": [{"date": "2026-11-01"}, {"date": "2026-11-02"}]}).json()

    assert "RECENCY_BIAS" not in _codes(data)


def test_recent_scope_change_reminds_about_onboarding_period(client):
    for key in ("recentScopeChange", "recent_scope_change"):
        data = client.post("/api/ai/consistency-check", json={**CONSISTENT_SHEET, key: True}).json()
        assert any(w["code"] == "RECENT_SCOPE_CHANGE" and "wdrożenia" in w["message"] for w in data["warnings"])
    assert _codes(client.post("/api/ai/consistency-check", json=CONSISTENT_SHEET).json()) == set()


def test_appreciation_nudge_builds_sbi_message_without_leaking_pii(client):
    body = {"employee_name": "Katarzyna Wiśniewska",
            "observations": ["Katarzyna Wiśniewska przejęła obsługę wniosków (k.wisniewska@mc.gov.pl)."],
            "completedTasks": ["Zamknięcie zaległych 40 spraw przed terminem kontroli"]}
    data = client.post("/api/ai/appreciation-nudge", json=body).json()

    assert "Wiśniewsk" not in data["masked_prompt"] and "@" not in data["masked_prompt"]
    assert data["message"].startswith("Katarzyna Wiśniewska") and "40 spraw" in data["situation"]
    assert data["situation"] and data["behavior"] and data["impact"]
    assert data["complianceMeta"]["redactedCount"] >= 3


def test_appreciation_nudge_without_observations_asks_to_fill_in(client):
    data = client.post("/api/ai/appreciation-nudge", json={}).json()

    assert "[Uzupełnij" in data["message"]


def test_overload_card_forbids_diagnoses_and_focuses_on_tasks(client):
    body = {"employee_name": "Jan Kowalski", "sheet_type": "C", "stage": "EVALUATION",
            "difficultScenario": "OVERLOAD_SIGNALS"}
    card = client.post("/api/ai/interview-prep", json=body).json()["difficult_conversation"]

    assert card["scenario"] == "OVERLOAD_SIGNALS"
    assert any("nie stawiaj diagnoz medycznych ani psychologicznych" in b for b in card["role_boundaries"])
    assert any("priorytet" in q.lower() for q in card["structure"] + card["questions"])


def test_negative_evaluation_card_communicates_right_to_appeal(client):
    body = {"employee_name": "Jan Kowalski", "sheet_type": "C", "stage": "EVALUATION",
            "difficult_scenario": "NEGATIVE_EVALUATION"}
    card = client.post("/api/ai/interview-prep", json=body).json()["difficult_conversation"]

    assert any("sprzeciw" in b and "7 dni" in b for b in card["role_boundaries"])


def test_interview_prep_without_scenario_has_no_card_and_rejects_unknown(client):
    body = {"employee_name": "Jan Kowalski", "sheet_type": "C", "stage": "EVALUATION"}
    assert client.post("/api/ai/interview-prep", json=body).json()["difficult_conversation"] is None
    assert client.post("/api/ai/interview-prep", json={**body, "difficultScenario": "X"}).status_code == 422


@pytest.mark.parametrize("criteria", [[{"name": "Rzetelność", "score": 3}], []])
def test_iprz_suggestions_always_have_free_70_20_10_sections(client, criteria):
    body = {"employee_name": "Jan Kowalski", "sheet_type": "C", "stage": "EVALUATION", "criteria_scores": criteria}
    catalog = client.post("/api/ai/interview-prep", json=body).json()["development_catalog"]

    assert catalog
    for entry in catalog:
        assert entry["estimatedCostPln"] == 0
        assert entry["onTheJob70"] and entry["social20"] and entry["formal10"]
        assert any("KSAP" in a for a in entry["formal10"])
        assert any("Shadowing" in a for a in entry["onTheJob70"])
        assert any("Mentoring" in a for a in entry["social20"])


def test_team_mentoring_pairs_strengths_with_development_areas(client):
    body = {"members": [
        {"alias": "P1", "strongCriteria": ["K08"], "developmentAreas": ["Komunikacja"]},
        {"alias": "P2", "strongCriteria": ["Komunikacja"], "developmentAreas": ["Rzetelność"]},
        {"alias": "P3", "strongCriteria": [], "developmentAreas": ["Zarządzanie strategiczne"]},
    ]}
    data = client.post("/api/ai/team-mentoring", json=body).json()

    pairs = {(p["mentor"], p["mentee"], p["criterion"]) for p in data["pairs"]}
    assert pairs == {("P2", "P1", "Komunikacja"), ("P1", "P2", "Rzetelność")}
    assert data["unmatched"] == [{"mentee": "P3", "criterion": "Zarządzanie strategiczne"}]


def test_team_mentoring_limits_mentor_load_and_masks_real_names(client):
    members = [{"alias": "Anna Nowak", "strongCriteria": ["K01"]}] + [
        {"alias": f"P{i}", "developmentAreas": ["K01"]} for i in range(3)]
    data = client.post("/api/ai/team-mentoring", json={"members": members}).json()

    assert "Nowak" not in data["masked_prompt"]
    assert len(data["pairs"]) == 2 and all(p["mentor"] == "Anna Nowak" for p in data["pairs"])
    assert len(data["unmatched"]) == 1
    assert client.post("/api/ai/team-mentoring", json={"members": members[:1]}).status_code == 422


@pytest.mark.parametrize("endpoint,body", [
    ("/api/ai/socratic-draft", {"employee_name": "Jan Kowalski", "sheet_type": "C", "notes": ["Jan Kowalski, tel. 600 100 200"]}),
    ("/api/ai/consistency-check", {**CONSISTENT_SHEET, "justification": "Anna Nowak pracowała rzetelnie."}),
    ("/api/ai/hr-partner", {"notes": ["Rozmowa z Piotrem Zielińskim o nadgodzinach."]}),
    ("/api/ai/interview-prep", {"employee_name": "Jan Kowalski", "sheet_type": "C", "stage": "INITIAL"}),
    ("/api/ai/suggest-criteria", {"position_title": "Referent", "sheet_type": "C", "job_description": "Kontakt: a@b.pl"}),
    ("/api/ai/appreciation-nudge", {"employee_name": "Jan Kowalski", "observations": ["Jan Kowalski wdrożył KSeF."]}),
    ("/api/ai/team-mentoring", {"members": [{"alias": "P1"}, {"alias": "P2"}]}),
])
def test_every_response_has_compliance_meta(client, endpoint, body):
    meta = client.post(endpoint, json=body).json()["complianceMeta"]

    assert meta["piiAnonymized"] is True and meta["humanInTheLoopRequired"] is True and meta["advisoryOnly"] is True
    assert meta["disclaimer"] == COMPLIANCE_DISCLAIMER
    expected_min = 0 if endpoint.endswith("team-mentoring") else 1
    assert meta["redactedCount"] >= expected_min


def test_redacted_count_counts_masked_occurrences():
    masker = PiiMasker(known_names=["Anna Nowak"])
    masker.mask("Anna Nowak napisała do Anny Nowak, kopia: a.nowak@urzad.gov.pl, PESEL 90010112345.")

    assert masker.redacted_count == 4
    assert PiiMasker(known_names=["Anna Nowak"]).redacted_count == 0


def test_hr_partner_masks_workload_signal_keys(client):
    data = client.post("/api/ai/hr-partner", json={"workload_signals": {"nadgodziny Jana Kowalskiego": 25}}).json()

    assert "Kowalsk" not in data["masked_prompt"]
