"""Every environment-backed setting this service has, in one place.

Read once at import and validated eagerly, so a bad value fails the process with a clear
message instead of a raw ValueError traceback or a silently wrong default. This mirrors the
validated @ConfigurationProperties records on the Spring side.
"""
from __future__ import annotations

import os

_REPO_ROOT = os.path.dirname(  # <repo>
    os.path.dirname(  # <repo>/python-service
        os.path.dirname(os.path.abspath(__file__))  # <repo>/python-service/app
    )
)

_DEFAULT_POLICY_PATH = os.path.join(_REPO_ROOT, "data", "policies.json")
_DEFAULT_MAX_DOCUMENT_BYTES = 5 * 1024 * 1024


class ConfigError(RuntimeError):
    """A setting was present but unusable. Raised at startup, never per request."""


def _positive_int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or not raw.strip():
        return default
    try:
        value = int(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} must be an integer number of bytes, got {raw!r}") from exc
    if value <= 0:
        raise ConfigError(f"{name} must be positive, got {value}")
    return value


#: Where the policy corpus is loaded from.
POLICY_FILE = os.environ.get("POLICY_FILE") or _DEFAULT_POLICY_PATH

#: Upper bound on a single uploaded document. This service is separately addressable, so it
#: cannot rely on the calling service's upload limits.
MAX_DOCUMENT_BYTES = _positive_int("MAX_DOCUMENT_BYTES", _DEFAULT_MAX_DOCUMENT_BYTES)

#: Default model provider mode. Overridable per request by the X-Model-Mode header, so this is
#: only the fallback; the value is validated in the providers package where the modes are known.
MODEL_PROVIDER_MODE = os.environ.get("MODEL_PROVIDER_MODE", "offline")
