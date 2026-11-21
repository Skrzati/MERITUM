from typing import Dict, List, Literal, Optional

from fastapi import FastAPI
from pydantic import BaseModel, Field

from . import assistants, consistency
from .guardrails import AI_DISCLAIMER
from .llm import LLMClient

app = FastAPI(title="MERITUM AI & Privacy Gateway", version="0.5.0")
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
    stage: Literal["INITIAL", "EVALUATION"]
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
