"""The public wire contract of the python service.

Every shape here is serialised to or deserialised from an HTTP body, so changing one is a
contract change that the calling service must be checked against. Shapes used only between
modules live in internal.py."""
from __future__ import annotations

from datetime import date
from typing import Literal, Optional

from pydantic import BaseModel, Field

AnswerStatus = Literal["ANSWERED", "INSUFFICIENT_EVIDENCE", "CONFLICT"]


class Citation(BaseModel):
    chunk_id: str
    quote: str


class AnswerRequest(BaseModel):
    tenant: str
    role: str
    as_of: date
    question: str = Field(min_length=1)


class AnswerResponse(BaseModel):
    status: AnswerStatus
    answer: Optional[str] = None
    citations: list[Citation] = []


class ExtractedFields(BaseModel):
    benefit: Optional[str] = None
    amount: Optional[float] = None
    currency: Optional[str] = None
    reference: Optional[str] = None


class FieldEvidence(BaseModel):
    """Verbatim source quotation for each supported extracted value."""

    benefit: Optional[str] = None
    amount: Optional[str] = None
    currency: Optional[str] = None
    reference: Optional[str] = None


class Issue(BaseModel):
    code: str
    detail: str


class DocumentResult(BaseModel):
    extracted: ExtractedFields
    field_evidence: FieldEvidence
    policy: Optional[AnswerResponse] = None
    issues: list[Issue] = []


class ErrorResponse(BaseModel):
    error_code: str
    message: str
