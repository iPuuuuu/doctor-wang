from datetime import datetime, timezone
from typing import Literal
from pydantic import BaseModel, Field


class IngestionRequest(BaseModel):
    requestId: str = Field(min_length=1)
    reportId: int = Field(gt=0)
    userId: int = Field(gt=0)
    namespace: str = Field(pattern=r"^report:\d+:\d+$")
    objectKey: str
    fileName: str = Field(min_length=1)
    contentType: str
    fileBase64: str | None = None
    fileSize: int = Field(default=0, ge=0)
    sourceVersion: str = "upload-v1"
    traceId: str


class IngestionResponse(BaseModel):
    jobId: str
    status: str
    acceptedAt: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class Citation(BaseModel):
    sourceType: str = "report"
    sourceId: str
    chunkId: str
    pageNo: int | None = None
    excerpt: str
    confidence: float = Field(ge=0, le=1)
    documentVersion: str


class RagRequest(BaseModel):
    requestId: str = Field(min_length=1)
    userId: int = Field(gt=0)
    reportId: int = Field(gt=0)
    question: str = Field(min_length=1, max_length=4000)
    namespaceHints: list[str] = Field(min_length=1)
    topK: int = Field(default=5, ge=1, le=20)
    traceId: str
    # 安心话聊模式（疑病症/大病焦虑缓解）：后端传入用户健康档案摘要，提示词与安全边界不同
    mode: str = Field(default="general", pattern="^(general|anxiety)$")
    healthProfile: str = Field(default="", max_length=2000)
    # 多轮对话上下文（最近若干条历史消息），提升追问连贯性；为空则忽略
    conversationHistory: str = Field(default="", max_length=4000)


class RagResponse(BaseModel):
    requestId: str
    status: Literal["OK", "INSUFFICIENT_EVIDENCE", "SAFETY_BLOCKED"]
    riskLevel: Literal["LOW", "MEDIUM", "HIGH"]
    summary: str
    citations: list[Citation]
    warnings: list[str]
    needsReview: bool = False
    traceId: str
