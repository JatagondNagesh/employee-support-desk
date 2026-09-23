import pytest

from app.config import ConfigError, _positive_int


def test_absent_setting_falls_back_to_the_default(monkeypatch):
    monkeypatch.delenv("MAX_DOCUMENT_BYTES", raising=False)
    assert _positive_int("MAX_DOCUMENT_BYTES", 123) == 123


def test_non_numeric_setting_is_a_clear_config_error(monkeypatch):
    monkeypatch.setenv("MAX_DOCUMENT_BYTES", "5MB")
    with pytest.raises(ConfigError):
        _positive_int("MAX_DOCUMENT_BYTES", 123)


def test_non_positive_setting_is_rejected(monkeypatch):
    monkeypatch.setenv("MAX_DOCUMENT_BYTES", "0")
    with pytest.raises(ConfigError):
        _positive_int("MAX_DOCUMENT_BYTES", 123)
