from datetime import date

import pytest
from conftest import read_request

from app.services.extraction_service import DocumentError, extract
from app.providers.model_provider import OfflineDeterministicProvider
from app.services.review_service import process_document

AS_OF = date(2026, 9, 21)


def codes(issues):
    return [issue.code for issue in issues]


def test_txt_extraction_with_source_quotations():
    result = extract("request-01.txt", read_request("request-01.txt"))
    assert result.extracted.benefit == "certification"
    assert result.extracted.amount == 18000
    assert result.extracted.currency == "INR"
    assert result.extracted.reference == "CERT-101"
    assert "INR 18000" in result.field_evidence.amount
    assert result.field_evidence.reference == "Reference: CERT-101"


def test_pdf_extraction():
    result = extract("request-02.pdf", read_request("request-02.pdf"))
    assert result.extracted.benefit == "home-office"
    assert result.extracted.amount == 14000
    assert result.extracted.reference == "HOME-202"


def test_ambiguous_amount_stays_null_and_visible():
    result = extract("request-03.txt", read_request("request-03.txt"))
    assert result.extracted.amount is None
    assert result.field_evidence.amount is None
    assert "AMBIGUOUS_AMOUNT" in codes(result.issues)


def test_missing_amount_is_reported():
    result = extract("request-07.txt", read_request("request-07.txt"))
    assert result.extracted.amount is None
    assert "MISSING_AMOUNT" in codes(result.issues)


def test_empty_document_raises_document_error():
    with pytest.raises(DocumentError) as exc:
        extract("request-08.txt", read_request("request-08.txt"))
    assert exc.value.code == "EMPTY_DOCUMENT"


def test_unreadable_pdf_raises_document_error():
    with pytest.raises(DocumentError) as exc:
        extract("broken.pdf", b"%PDF-1.4 definitely not a pdf")
    assert exc.value.code == "UNREADABLE_DOCUMENT"


def test_injected_document_cannot_change_tenant_or_approve():
    result = process_document(
        "Atlas",
        "employee",
        AS_OF,
        "request-05.txt",
        read_request("request-05.txt"),
        provider=OfflineDeterministicProvider(),
    )
    assert "UNTRUSTED_INSTRUCTIONS_IGNORED" in codes(result.issues)
    assert [c.chunk_id for c in result.policy.citations] == ["atlas-cert-current"]
    assert "approved" not in (result.policy.answer or "").lower()


def test_review_report_never_implies_payability():
    result = process_document(
        "Atlas",
        "employee",
        AS_OF,
        "request-01.txt",
        read_request("request-01.txt"),
        provider=OfflineDeterministicProvider(),
    )
    assert result.policy.status == "ANSWERED"
    assert "ANNUAL_LIMIT_NOT_A_BALANCE" in codes(result.issues)
    assert "HUMAN_REVIEW_REQUIRED" in codes(result.issues)


def test_policy_conflict_and_input_ambiguity_are_distinguishable():
    conflicted = process_document(
        "Atlas",
        "employee",
        AS_OF,
        "request-02.pdf",
        read_request("request-02.pdf"),
        provider=OfflineDeterministicProvider(),
    )
    ambiguous = process_document(
        "Atlas",
        "employee",
        AS_OF,
        "request-03.txt",
        read_request("request-03.txt"),
        provider=OfflineDeterministicProvider(),
    )
    assert "POLICY_CONFLICT" in codes(conflicted.issues)
    assert "AMBIGUOUS_AMOUNT" not in codes(conflicted.issues)
    assert "AMBIGUOUS_AMOUNT" in codes(ambiguous.issues)
    assert "POLICY_CONFLICT" not in codes(ambiguous.issues)


def test_condition_only_policy_does_not_claim_an_annual_limit():
    result = process_document(
        "Atlas",
        "employee",
        AS_OF,
        "request-07.txt",
        read_request("request-07.txt"),
        provider=OfflineDeterministicProvider(),
    )
    assert result.policy.status == "ANSWERED"
    assert [c.chunk_id for c in result.policy.citations] == ["atlas-training-current"]
    assert "MISSING_AMOUNT" in codes(result.issues)
    assert "ANNUAL_LIMIT_NOT_A_BALANCE" not in codes(result.issues)


def test_benefit_without_policy_reports_no_applicable_policy():
    result = process_document(
        "Atlas",
        "employee",
        AS_OF,
        "request-04.txt",
        read_request("request-04.txt"),
        provider=OfflineDeterministicProvider(),
    )
    assert result.extracted.benefit == "wellness"
    assert result.policy.status == "INSUFFICIENT_EVIDENCE"
    assert "NO_APPLICABLE_POLICY" in codes(result.issues)
