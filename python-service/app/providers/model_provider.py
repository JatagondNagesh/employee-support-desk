"""Model providers.

The offline deterministic provider is the default double so the application and
tests run without any paid service or API key. Failure modes are selectable so
timeout, unavailable-provider and malformed-output behaviour can be exercised.

Model output is untrusted: it is returned as raw text and validated by
policy_service before it can reach the public response.
"""
from __future__ import annotations

import json
from typing import Protocol

from ..config import MODEL_PROVIDER_MODE


class ProviderTimeout(Exception):
    """The provider did not answer in time.

    Nothing in this service imposes the timeout today: the bound that actually applies is the
    calling service's read timeout. Only TimeoutProvider raises this, so the handling path stays
    exercised until a real provider with its own deadline is wired in.
    """


class ProviderUnavailable(Exception):
    """The provider could not be reached."""


class UnknownProviderMode(ValueError):
    """A caller or the environment asked for a provider mode that does not exist."""


class ModelProvider(Protocol):
    def generate(self, question: str, passages: list[dict]) -> str:
        """Return raw model output. Exactly one attempt, no retries."""


class OfflineDeterministicProvider:
    """Composes an answer only from the eligible passages it is given."""

    def generate(self, question: str, passages: list[dict]) -> str:
        quoted = " ".join(p["text"] for p in passages)
        answer = (
            f"Applicable policy for the caller on the requested date: {quoted} "
            "This states the policy terms only; it does not confirm remaining balance, "
            "expense eligibility, or a payable amount."
        )
        return json.dumps(
            {"answer": answer, "citation_ids": [p["chunk_id"] for p in passages]}
        )


class TimeoutProvider:
    def generate(self, question: str, passages: list[dict]) -> str:
        raise ProviderTimeout("model call exceeded the configured timeout")


class UnavailableProvider:
    def generate(self, question: str, passages: list[dict]) -> str:
        raise ProviderUnavailable("model provider is unreachable")


class MalformedProvider:
    def generate(self, question: str, passages: list[dict]) -> str:
        return "{not json at all"


class FabricatingProvider:
    """Returns well-formed JSON citing a chunk that was never supplied."""

    def generate(self, question: str, passages: list[dict]) -> str:
        return json.dumps(
            {"answer": "Every allowance is INR 999999.", "citation_ids": ["boreal-cert-current"]}
        )


_MODES = {
    "offline": OfflineDeterministicProvider,
    "timeout": TimeoutProvider,
    "unavailable": UnavailableProvider,
    "malformed": MalformedProvider,
    "fabricating": FabricatingProvider,
}


def get_provider(mode: str | None = None) -> ModelProvider:
    selected = (mode or MODEL_PROVIDER_MODE).strip().lower()
    if selected not in _MODES:
        raise UnknownProviderMode(
            f"unknown model provider mode '{selected}'; expected one of {sorted(_MODES)}"
        )
    return _MODES[selected]()
