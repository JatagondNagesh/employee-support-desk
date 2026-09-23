from fastapi.testclient import TestClient
from conftest import read_request

from app.services.extraction_service import MAX_DOCUMENT_BYTES
from app.main import app

client = TestClient(app)


def test_answer_endpoint_returns_citation_contract():
    response = client.post(
        "/answer",
        json={
            "tenant": "Atlas",
            "role": "employee",
            "as_of": "2026-09-21",
            "question": "What is my annual certification reimbursement limit?",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ANSWERED"
    assert body["citations"][0]["chunk_id"] == "atlas-cert-current"


def test_answer_endpoint_maps_provider_failures_to_non_2xx():
    for mode, status, code in (
        ("timeout", 504, "MODEL_TIMEOUT"),
        ("unavailable", 503, "MODEL_UNAVAILABLE"),
        ("malformed", 502, "MODEL_MALFORMED_OUTPUT"),
    ):
        response = client.post(
            "/answer",
            headers={"X-Model-Mode": mode},
            json={
                "tenant": "Atlas",
                "role": "employee",
                "as_of": "2026-09-21",
                "question": "What is my annual certification reimbursement limit?",
            },
        )
        assert response.status_code == status
        assert response.json()["error_code"] == code


def test_startup_loads_the_policy_corpus():
    # Entering the context manager runs the lifespan hook; a broken corpus would fail here.
    with TestClient(app) as started:
        assert started.get("/health").status_code == 200


def test_unknown_model_mode_is_a_client_error_not_a_500():
    response = client.post(
        "/answer",
        headers={"X-Model-Mode": "definitely-not-a-mode"},
        json={
            "tenant": "Atlas",
            "role": "employee",
            "as_of": "2026-09-21",
            "question": "What is my annual certification reimbursement limit?",
        },
    )
    assert response.status_code == 400
    assert response.json()["error_code"] == "UNKNOWN_MODEL_MODE"


def test_process_endpoint_rejects_an_oversized_document():
    oversized = b"a" * (MAX_DOCUMENT_BYTES + 1)
    response = client.post(
        "/documents/process",
        data={"tenant": "Atlas", "role": "employee", "as_of": "2026-09-21"},
        files={"file": ("big.txt", oversized, "text/plain")},
    )
    assert response.status_code == 422
    assert response.json()["error_code"] == "DOCUMENT_TOO_LARGE"


def test_process_endpoint_handles_empty_file():
    response = client.post(
        "/documents/process",
        data={"tenant": "Atlas", "role": "employee", "as_of": "2026-09-21"},
        files={"file": ("request-08.txt", read_request("request-08.txt"), "text/plain")},
    )
    assert response.status_code == 422
    assert response.json()["error_code"] == "EMPTY_DOCUMENT"


def test_process_endpoint_returns_document_result():
    response = client.post(
        "/documents/process",
        data={
            "tenant": "Atlas",
            "role": "employee",
            "as_of": "2026-09-21",
            "batch_id": "demo-01",
            "document_id": "request-01",
        },
        files={"file": ("request-01.txt", read_request("request-01.txt"), "text/plain")},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["extracted"]["amount"] == 18000
    assert body["policy"]["status"] == "ANSWERED"
