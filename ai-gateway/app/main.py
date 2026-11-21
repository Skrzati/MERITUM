import json
from typing import Dict, List, Literal, Optional

from fastapi import FastAPI
from pydantic import BaseModel, Field

from . import assistants, consistency, criteria_suggest
from .guardrails import AI_DISCLAIMER
from .llm import LLMClient

MAX_BODY_BYTES = 64 * 1024
PAYLOAD_TOO_LARGE = 413


class BodySizeLimit:
    """Odrzuca żądania > MAX_BODY_BYTES (413) — także bez nagłówka Content-Length (chunked)."""

    def __init__(self, app, max_bytes: int = MAX_BODY_BYTES):
        self.app, self.max_bytes = app, max_bytes

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        length = dict(scope.get("headers") or []).get(b"content-length")
        if length is not None and length.isdigit() and int(length) > self.max_bytes:
            return await self._reject(send)
        received = 0
        chunks = []
        while True:
            message = await receive()
            received += len(message.get("body", b""))
            if received > self.max_bytes:
                return await self._reject(send)
            chunks.append(message)
            if not message.get("more_body"):
                break
        pending = iter(chunks)

        async def replay():
            return next(pending, {"type": "http.request", "body": b"", "more_body": False})

        return await self.app(scope, replay, send)

    async def _reject(self, send):
        body = json.dumps({"detail": f"Żądanie przekracza limit {self.max_bytes // 1024} KB"}).encode()
        await send({"type": "http.response.start", "status": PAYLOAD_TOO_LARGE,
                    "headers": [(b"content-type", b"application/json"), (b"content-length", str(len(body)).encode())]})
        await send({"type": "http.response.body", "body": body})


app = FastAPI(title="MERITUM AI & Privacy Gateway", version="0.6.0")
app.add_middleware(BodySizeLimit)
llm = LLMClient()


class CriterionScoreIn(BaseModel):
    name: str
    score: Optional[int] = None
    code: Optional[str] = None


class SocraticRequest(BaseModel):
    employee_name: str
    sheet_type: Literal["A", "B", "C"]
    criteria_scores: List[CriterionScoreIn] = Field(default_factory=list)
    notes: List[str] = Field(default_factory=list)


class SocraticResponse(BaseModel):
    masked_prompt: str
    socratic_questions: List[str]
    draft_justification: str
    ai_disclaimer: str = AI_DISCLAIMER
    mode: str


class ConsistencyRequest(BaseModel):
    employee_name: str
    employee_gender: Optional[str] = None
    criteria_part1: List[str] = Field(default_factory=list)
    criteria_part2: List[str] = Field(default_factory=list)
    average_score: float = Field(ge=1, le=9)
    level: Optional[str] = None
    justification: str = ""
    grade_request_text: Optional[str] = None


class WarningOut(BaseModel):
    code: str
    severity: str
    message: str


class ConsistencyResponse(BaseModel):
    warnings: List[WarningOut]
    auto_modified: bool = False


class HrPartnerRequest(BaseModel):
    notes: List[str] = Field(default_factory=list)
    workload_signals: Dict[str, float] = Field(default_factory=dict)


class HrPartnerResponse(BaseModel):
    masked_prompt: str
    burnout_risk_signal: bool
    conversation_starters: List[str]
    organizational_suggestions: List[str]
    appreciation_moment: str
    ai_disclaimer: str = AI_DISCLAIMER
    mode: str


class InterviewPrepRequest(BaseModel):
    employee_name: str
    sheet_type: Literal["A", "B", "C"]
    stage: Literal["INITIAL", "EVALUATION", "FEEDBACK"]
    criteria_scores: List[CriterionScoreIn] = Field(default_factory=list)
    notes: List[str] = Field(default_factory=list)


class AgendaItem(BaseModel):
    topic: str
    legal_basis: str
    questions: List[str]


class DifficultSituation(BaseModel):
    situation: str
    tip: str


class DevelopmentEntry(BaseModel):
    criterion: str
    actions: List[str]


class InterviewPrepResponse(BaseModel):
    masked_prompt: str
    stage: str
    goal: str
    agenda: List[AgendaItem]
    difficult_situations: List[DifficultSituation]
    development_catalog: List[DevelopmentEntry]
    ai_disclaimer: str = AI_DISCLAIMER
    mode: str


class SuggestCriteriaRequest(BaseModel):
    position_title: str
    department: str = ""
    sheet_type: Literal["A", "B", "C"]
    mandatory_codes: List[str] = Field(default_factory=list)
    responsibilities_summary: str = ""


class CriterionSuggestion(BaseModel):
    code: str
    name: str
    reason: str
    expectations: List[str]


class SuggestCriteriaResponse(BaseModel):
    masked_prompt: str
    suggestions: List[CriterionSuggestion]
    ai_disclaimer: str = AI_DISCLAIMER
    mode: str


@app.get("/health")
def health() -> Dict[str, str]:
    return {"status": "UP", "service": "ai-gateway", "llm_mode": llm.mode, "llm_model": llm.model}


@app.post("/api/ai/socratic-draft", response_model=SocraticResponse)
def socratic_draft(req: SocraticRequest) -> Dict:
    criteria = [assistants.CriterionScore(c.name, c.score, c.code) for c in req.criteria_scores]
    return assistants.socratic_draft(llm, req.employee_name, req.sheet_type, criteria, req.notes)


@app.post("/api/ai/consistency-check", response_model=ConsistencyResponse)
def consistency_check(req: ConsistencyRequest) -> ConsistencyResponse:
    findings = consistency.check(req.employee_name, req.employee_gender, req.criteria_part1, req.criteria_part2,
                                 req.average_score, req.level, req.justification, req.grade_request_text)
    return ConsistencyResponse(warnings=[WarningOut(**f.__dict__) for f in findings], auto_modified=False)


@app.post("/api/ai/hr-partner", response_model=HrPartnerResponse)
def hr_partner(req: HrPartnerRequest) -> Dict:
    return assistants.hr_partner(llm, req.notes, req.workload_signals)


@app.post("/api/ai/interview-prep", response_model=InterviewPrepResponse)
def interview_prep(req: InterviewPrepRequest) -> Dict:
    criteria = [assistants.CriterionScore(c.name, c.score, c.code) for c in req.criteria_scores]
    return assistants.interview_prep(llm, req.employee_name, req.sheet_type, req.stage, criteria, req.notes)


@app.post("/api/ai/suggest-criteria", response_model=SuggestCriteriaResponse)
def suggest_criteria(req: SuggestCriteriaRequest) -> Dict:
    return criteria_suggest.suggest_criteria(llm, req.position_title, req.department, req.sheet_type,
                                             req.mandatory_codes, req.responsibilities_summary)
