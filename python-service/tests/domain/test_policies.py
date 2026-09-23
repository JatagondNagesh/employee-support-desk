from datetime import date

import pytest

from app.domain.policies import PolicyDataError, eligible_policies, load_policies


def test_missing_policy_corpus_fails_loudly_rather_than_silently():
    with pytest.raises(PolicyDataError):
        load_policies("does-not-exist.json")


def test_eligibility_uses_metadata_only():
    on_date = eligible_policies("Atlas", "employee", date(2026, 9, 21))
    assert on_date, "expected at least one approved, in-window Atlas employee policy"
    assert all(r.tenant == "Atlas" and r.role == "employee" for r in on_date)
    assert all(r.approval_state == "Approved" for r in on_date)
