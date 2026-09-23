"""Combines extraction and policy findings into a human-review report."""
from __future__ import annotations

from datetime import date
from typing import Optional

from .extraction_service import extract
from ..providers.model_provider import ModelProvider
from ..models.wire import DocumentResult, Issue
from .policy_service import answer_question, build_question

ALWAYS_REVIEW = Issue(
    code="HUMAN_REVIEW_REQUIRED",
    detail="Every submitted request requires human review. This service does not approve "
    "claims, determine payable amounts, or contact employees.",
)

LIMIT_NOT_BALANCE = Issue(
    code="ANNUAL_LIMIT_NOT_A_BALANCE",
    detail="The cited annual policy limit does not establish remaining balance, expense "
    "eligibility, or a payable amount. No claims history is available.",
)


def process_document(
    tenant: str,
    role: str,
    as_of: date,
    filename: str,
    content: bytes,
    provider: Optional[ModelProvider] = None,
) -> DocumentResult:
    extraction = extract(filename, content)
    issues = list(extraction.issues)

    policy = None
    benefit = extraction.extracted.benefit
    if benefit:
        policy = answer_question(
            tenant, role, as_of, build_question(benefit), provider=provider
        )
        if policy.status == "INSUFFICIENT_EVIDENCE":
            issues.append(
                Issue(
                    code="NO_APPLICABLE_POLICY",
                    detail=f"No approved policy for the caller covers '{benefit}' on the "
                    "requested date.",
                )
            )
        elif policy.status == "CONFLICT":
            issues.append(
                Issue(
                    code="POLICY_CONFLICT",
                    detail="Simultaneously applicable approved policies disagree. No "
                    "precedence rule is defined, so the conflict is left unresolved.",
                )
            )
        else:
            if any("INR" in citation.quote for citation in policy.citations):
                issues.append(LIMIT_NOT_BALANCE)
            issues.append(
                Issue(
                    code="POLICY_CONDITIONS_NOT_VERIFIED",
                    detail="Conditions stated in the cited policy were not verified against "
                    "this request.",
                )
            )
    else:
        issues.append(
            Issue(
                code="POLICY_NOT_RETRIEVED",
                detail="No policy lookup was performed because the requested benefit could "
                "not be identified.",
            )
        )

    issues.append(ALWAYS_REVIEW)
    return DocumentResult(
        extracted=extraction.extracted,
        field_evidence=extraction.field_evidence,
        policy=policy,
        issues=issues,
    )
