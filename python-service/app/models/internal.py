"""Shapes used between modules that never cross the wire.

Kept apart from wire.py so the public contract stays the only thing a reader has to check
when reasoning about what this service exposes. Changing anything here is safe; changing
wire.py is a contract change.
"""
from __future__ import annotations

from pydantic import BaseModel

from .wire import ExtractedFields, FieldEvidence, Issue


class ExtractionResult(BaseModel):
    """What extraction hands to review. Never serialised to a client."""

    extracted: ExtractedFields
    field_evidence: FieldEvidence
    issues: list[Issue] = []
