"""Agent-specific schemas for multi-step health reasoning."""
from typing import Literal
from pydantic import BaseModel, Field

from .schemas import Citation


class AgentRequest(BaseModel):
    requestId: str = Field(min_length=1)
    userId: int = Field(gt=0)
    conversationId: str | None = None  # None = new conversation
    message: str = Field(min_length=1, max_length=4000)
    reportIds: list[int] = Field(default_factory=list)  # relevant report IDs
    traceId: str


class AgentStep(BaseModel):
    """A single reasoning step in the agent chain."""
    stepType: Literal["SAFETY_CHECK", "REPORT_LOOKUP", "SYMPTOM_CHECKER",
                       "DRUG_INTERACTION", "SYNTHESIZE", "FALLBACK"]
    summary: str
    findings: list[str] = Field(default_factory=list)
    citations: list[Citation] = Field(default_factory=list)
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    needsReview: bool = False


class AgentResponse(BaseModel):
    requestId: str
    conversationId: str
    status: Literal["OK", "SAFETY_BLOCKED", "INSUFFICIENT_EVIDENCE",
                     "NEEDS_CLARIFICATION", "ERROR"]
    riskLevel: Literal["LOW", "MEDIUM", "HIGH"]
    answer: str
    reasoningSteps: list[AgentStep] = Field(default_factory=list)
    citations: list[Citation] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    followUpQuestions: list[str] = Field(default_factory=list)
    needsReview: bool = False
    traceId: str


class ConversationTurn(BaseModel):
    role: Literal["user", "agent"]
    message: str
    agentResponse: AgentResponse | None = None
