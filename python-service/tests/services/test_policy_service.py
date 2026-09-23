from datetime import date

import pytest

from app.providers.model_provider import (
    FabricatingProvider,
    MalformedProvider,
    OfflineDeterministicProvider,
    TimeoutProvider,
    UnavailableProvider,
    ProviderTimeout,
    ProviderUnavailable,
)
from app.services.policy_service import MalformedModelOutput, answer_question, build_question

AS_OF = date(2026, 9, 21)
CERT_Q = build_question("certification")
HOME_Q = build_question("home-office")


def answer(tenant="Atlas", role="employee", as_of=AS_OF, question=CERT_Q, provider=None):
    return answer_question(
        tenant, role, as_of, question, provider=provider or OfflineDeterministicProvider()
    )


def test_current_certification_policy_is_answered_with_verbatim_quote():
    result = answer()
    assert result.status == "ANSWERED"
    assert [c.chunk_id for c in result.citations] == ["atlas-cert-current"]
    assert (
        result.citations[0].quote
        == "The annual certification reimbursement limit for employees is INR 25000."
    )
    assert "INR 25000" in result.answer


def test_historical_date_selects_historical_record():
    result = answer(as_of=date(2026, 5, 31))
    assert [c.chunk_id for c in result.citations] == ["atlas-cert-historical"]


def test_interval_end_date_is_excluded():
    result = answer(as_of=date(2026, 6, 1))
    assert [c.chunk_id for c in result.citations] == ["atlas-cert-current"]


def test_contractor_sees_only_contractor_policy():
    result = answer(role="contractor")
    assert [c.chunk_id for c in result.citations] == ["atlas-cert-contractor"]


def test_other_tenant_is_isolated():
    result = answer(tenant="Boreal")
    assert [c.chunk_id for c in result.citations] == ["boreal-cert-current"]


def test_draft_record_is_never_cited():
    result = answer()
    assert "atlas-cert-draft" not in [c.chunk_id for c in result.citations]


def test_conflicting_home_office_policies_return_conflict():
    result = answer(question=HOME_Q)
    assert result.status == "CONFLICT"
    assert result.answer is None
    assert sorted(c.chunk_id for c in result.citations) == [
        "atlas-home-office-a",
        "atlas-home-office-b",
    ]


def test_unknown_benefit_returns_insufficient_evidence():
    result = answer(question="What wellness benefit am I entitled to?")
    assert result.status == "INSUFFICIENT_EVIDENCE"
    assert result.answer is None
    assert result.citations == []


def test_contractor_home_office_has_no_eligible_policy():
    result = answer(role="contractor", question=HOME_Q)
    assert result.status == "INSUFFICIENT_EVIDENCE"


def test_injection_passage_is_not_retrieved_for_any_benefit():
    for benefit in ("certification", "home-office", "travel", "training"):
        result = answer(question=build_question(benefit))
        assert "atlas-injection-example" not in [c.chunk_id for c in result.citations]
        assert "999999" not in (result.answer or "")


def test_timeout_is_a_technical_failure_not_insufficient_evidence():
    with pytest.raises(ProviderTimeout):
        answer(provider=TimeoutProvider())


def test_unavailable_provider_raises():
    with pytest.raises(ProviderUnavailable):
        answer(provider=UnavailableProvider())


def test_malformed_output_raises():
    with pytest.raises(MalformedModelOutput):
        answer(provider=MalformedProvider())


def test_model_cannot_cite_evidence_that_was_not_supplied():
    with pytest.raises(MalformedModelOutput):
        answer(provider=FabricatingProvider())
