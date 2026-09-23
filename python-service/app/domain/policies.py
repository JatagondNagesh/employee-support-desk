"""Policy records and eligibility rules.

Policy records live in data/policies.json and are treated as untrusted data:
their text is never interpreted as instructions. Eligibility is decided from
metadata only (tenant, role, approval state, effective interval).
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import date
from functools import lru_cache
from typing import Optional

from ..config import POLICY_FILE

DEFAULT_POLICY_PATH = POLICY_FILE

# Topic keywords. A question and a policy passage are considered relevant to
# each other only when they resolve to the same benefit topic.
BENEFIT_KEYWORDS: dict[str, tuple[str, ...]] = {
    "certification": ("certification", "certificate", "cert reimbursement"),
    "home-office": ("home-office", "home office", "desk", "chair"),
    "travel": ("travel", "rail", "business trip"),
    "training": ("training", "course booking"),
    "wellness": ("wellness", "gym", "fitness"),
}


@dataclass(frozen=True)
class PolicyRecord:
    chunk_id: str
    tenant: str
    role: str
    approval_state: str
    effective_from: date
    effective_to: date
    text: str

    @property
    def benefit(self) -> Optional[str]:
        return classify_benefit(self.text)


def classify_benefit(text: str) -> Optional[str]:
    """Map free text to one benefit topic, or None when no topic is recognised."""
    lowered = text.lower()
    for benefit, keywords in BENEFIT_KEYWORDS.items():
        if any(keyword in lowered for keyword in keywords):
            return benefit
    return None


class PolicyDataError(RuntimeError):
    """The policy corpus is missing or unusable. Raised at startup, never per request."""


@lru_cache(maxsize=1)
def load_policies(path: str = DEFAULT_POLICY_PATH) -> tuple[PolicyRecord, ...]:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            raw = json.load(handle)
        records = tuple(
            PolicyRecord(
                chunk_id=item["chunk_id"],
                tenant=item["tenant"],
                role=item["role"],
                approval_state=item["approval_state"],
                effective_from=date.fromisoformat(item["effective_from"]),
                effective_to=date.fromisoformat(item["effective_to"]),
                text=item["text"],
            )
            for item in raw
        )
    except (OSError, ValueError, KeyError, TypeError) as exc:
        raise PolicyDataError(f"policy corpus at {path} could not be loaded: {exc}") from exc
    if not records:
        raise PolicyDataError(f"policy corpus at {path} is empty")
    return records


def eligible_policies(
    tenant: str,
    role: str,
    as_of: date,
    records: Optional[tuple[PolicyRecord, ...]] = None,
) -> list[PolicyRecord]:
    """Approved records for the caller's tenant/role whose interval covers as_of.

    The effective interval includes effective_from and excludes effective_to.
    """
    source = records if records is not None else load_policies()
    return [
        record
        for record in source
        if record.tenant.lower() == tenant.lower()
        and record.role.lower() == role.lower()
        and record.approval_state == "Approved"
        and record.effective_from <= as_of < record.effective_to
    ]


def relevant_policies(records: list[PolicyRecord], benefit: Optional[str]) -> list[PolicyRecord]:
    """Eligible records that discuss the requested benefit topic.

    Records with no recognised topic (for example the prompt-injection example)
    are never relevant, so they never reach generation or the response.
    """
    if benefit is None:
        return []
    return [record for record in records if record.benefit == benefit]
