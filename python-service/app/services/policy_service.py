"""Policy retrieval, conflict detection and answer generation."""
from __future__ import annotations

import json
import re
from datetime import date
from typing import Optional

from ..providers.model_provider import ModelProvider, get_provider
from ..models.wire import AnswerResponse, Citation
from ..domain.policies import (
    PolicyRecord,
    classify_benefit,
    eligible_policies,
    relevant_policies,
)

AMOUNT_IN_POLICY = re.compile(r"INR\s*([0-9][0-9,]*)", re.IGNORECASE)


class MalformedModelOutput(Exception):
    """Model output was unparsable or cited evidence that was not supplied."""


def policy_amount(record: PolicyRecord) -> Optional[int]:
    match = AMOUNT_IN_POLICY.search(record.text)
    return int(match.group(1).replace(",", "")) if match else None


def build_question(benefit: str) -> str:
    return f"What does the applicable {benefit} policy say?"


def answer_question(
    tenant: str,
    role: str,
    as_of: date,
    question: str,
    provider: Optional[ModelProvider] = None,
) -> AnswerResponse:
    provider = provider or get_provider()
    benefit = classify_benefit(question)
    eligible = eligible_policies(tenant, role, as_of)
    relevant = relevant_policies(eligible, benefit)

    if not relevant:
        return AnswerResponse(status="INSUFFICIENT_EVIDENCE", answer=None, citations=[])

    with_amounts = [r for r in relevant if policy_amount(r) is not None]
    distinct = {policy_amount(r) for r in with_amounts}
    if len(distinct) > 1:
        # Contradictory, simultaneously applicable policies. No precedence rule
        # is supplied, so the disagreement is reported instead of resolved.
        return AnswerResponse(
            status="CONFLICT",
            answer=None,
            citations=[Citation(chunk_id=r.chunk_id, quote=r.text) for r in with_amounts],
        )

    passages = [{"chunk_id": r.chunk_id, "text": r.text} for r in relevant]
    raw = provider.generate(question, passages)
    answer, citation_ids = _validate_model_output(raw, {p["chunk_id"] for p in passages})

    by_id = {r.chunk_id: r for r in relevant}
    citations = [Citation(chunk_id=cid, quote=by_id[cid].text) for cid in citation_ids]
    return AnswerResponse(status="ANSWERED", answer=answer, citations=citations)


def _validate_model_output(raw: str, allowed_ids: set[str]) -> tuple[str, list[str]]:
    try:
        payload = json.loads(raw)
    except (json.JSONDecodeError, TypeError) as exc:
        raise MalformedModelOutput("model output was not valid JSON") from exc

    if not isinstance(payload, dict):
        raise MalformedModelOutput("model output was not a JSON object")

    answer = payload.get("answer")
    citation_ids = payload.get("citation_ids")
    if not isinstance(answer, str) or not answer.strip():
        raise MalformedModelOutput("model output had no usable answer")
    if not isinstance(citation_ids, list) or not citation_ids:
        raise MalformedModelOutput("model output had no citations")
    unknown = [cid for cid in citation_ids if cid not in allowed_ids]
    if unknown:
        # Ineligible or invented evidence must never reach the response.
        raise MalformedModelOutput("model output cited evidence that was not supplied")
    return answer, citation_ids
