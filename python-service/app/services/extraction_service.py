"""Document text extraction and field extraction.

Submitted documents are untrusted data. Any instruction-like text found in them
is recorded as a review reason and never changes caller identity or behaviour.
"""
from __future__ import annotations

import io
import re
from typing import Optional

from ..config import MAX_DOCUMENT_BYTES
from ..models.internal import ExtractionResult
from ..models.wire import ExtractedFields, FieldEvidence, Issue
from ..domain.policies import BENEFIT_KEYWORDS, classify_benefit

AMOUNT = re.compile(r"(INR)\s*([0-9][0-9,]*)", re.IGNORECASE)
REFERENCE = re.compile(r"Reference:\s*([A-Za-z]+-[0-9]+)")

INJECTION_MARKERS = (
    "system message",
    "ignore the caller",
    "ignore all prior",
    "mark this request approved",
    "i belong to",
    "employee access",
)


class DocumentError(Exception):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def read_text(filename: str, content: bytes) -> str:
    if not content:
        raise DocumentError("EMPTY_DOCUMENT", "Document is empty.")
    if len(content) > MAX_DOCUMENT_BYTES:
        raise DocumentError(
            "DOCUMENT_TOO_LARGE",
            f"Document exceeds the {MAX_DOCUMENT_BYTES} byte limit.",
        )
    if filename.lower().endswith(".pdf"):
        return _read_pdf(content)
    try:
        text = content.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise DocumentError("UNREADABLE_DOCUMENT", "Document is not valid UTF-8 text.") from exc
    if not text.strip():
        raise DocumentError("EMPTY_DOCUMENT", "Document contains no readable text.")
    return text


def _read_pdf(content: bytes) -> str:
    try:
        from pypdf import PdfReader

        reader = PdfReader(io.BytesIO(content))
        text = "\n".join(page.extract_text() or "" for page in reader.pages)
    except Exception as exc:  # pypdf raises a variety of parse errors
        raise DocumentError("UNREADABLE_DOCUMENT", "PDF could not be parsed.") from exc
    if not text.strip():
        raise DocumentError(
            "UNREADABLE_DOCUMENT", "PDF contains no extractable text layer."
        )
    return text


def _sentences(text: str) -> list[str]:
    parts: list[str] = []
    for line in text.splitlines():
        for chunk in re.split(r"(?<=[.?!])\s+", line.strip()):
            if chunk.strip():
                parts.append(chunk.strip())
    return parts


def _quote_for(text: str, needle: str) -> Optional[str]:
    lowered = needle.lower()
    for sentence in _sentences(text):
        if lowered in sentence.lower():
            return sentence
    return None


def extract_fields(text: str) -> ExtractionResult:
    issues: list[Issue] = []
    extracted = ExtractedFields()
    evidence = FieldEvidence()

    benefit = classify_benefit(text)
    if benefit:
        keyword = next(
            k for k in BENEFIT_KEYWORDS[benefit] if k in text.lower()
        )
        extracted.benefit = benefit
        evidence.benefit = _quote_for(text, keyword)
    else:
        issues.append(
            Issue(
                code="BENEFIT_NOT_IDENTIFIED",
                detail="No requested benefit could be identified in the document text.",
            )
        )

    matches = AMOUNT.findall(text)
    distinct = {m[1].replace(",", "") for m in matches}
    if len(distinct) == 1:
        raw_amount = next(iter(distinct))
        extracted.amount = float(raw_amount)
        extracted.currency = matches[0][0].upper()
        quote = _quote_for(text, f"{matches[0][0]} {matches[0][1]}")
        evidence.amount = quote
        evidence.currency = quote
    elif len(distinct) > 1:
        stated = ", ".join(f"INR {value}" for value in sorted(distinct))
        issues.append(
            Issue(
                code="AMBIGUOUS_AMOUNT",
                detail=f"Document states more than one uncorrected amount ({stated}); "
                "the requested amount is unresolved.",
            )
        )
    else:
        issues.append(
            Issue(
                code="MISSING_AMOUNT",
                detail="No amount is stated in the document.",
            )
        )

    reference = REFERENCE.search(text)
    if reference:
        extracted.reference = reference.group(1)
        evidence.reference = _quote_for(text, reference.group(0))
    else:
        issues.append(
            Issue(code="MISSING_REFERENCE", detail="No reference is stated in the document.")
        )

    lowered = text.lower()
    if any(marker in lowered for marker in INJECTION_MARKERS):
        issues.append(
            Issue(
                code="UNTRUSTED_INSTRUCTIONS_IGNORED",
                detail="Document contains instruction-like text or identity claims. "
                "It was treated as data only; caller tenant and role come from the caller lookup.",
            )
        )

    return ExtractionResult(extracted=extracted, field_evidence=evidence, issues=issues)


def extract(filename: str, content: bytes) -> ExtractionResult:
    return extract_fields(read_text(filename, content))
