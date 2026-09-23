# Employee policy & reimbursement triage

Two local services:

- **spring-service** (Java 21 / Spring Boot 3.4, port 8080) — public API. Owns caller context,
  request and manifest validation, batch orchestration, exact-duplicate detection, failure
  isolation and the public response shape.
- **python-service** (FastAPI, port 8000) — document extraction (TXT + text-based PDF), policy
  retrieval, conflict detection, answer generation and review reasons.

Policy records live in `data/policies.json` (data only, never in code). Synthetic request documents
live in `data/requests/`.

## Documents

| document | what it covers |
| --- | --- |
| [`design-docs/01-requirements.md`](design-docs/01-requirements.md) | requirements, business rules, service split, acceptance criteria |
| [`design-docs/02-hld.md`](design-docs/02-hld.md) | context, responsibility split, key decisions, flows, failure model |
| [`design-docs/03-lld.md`](design-docs/03-lld.md) | packages, algorithms, validation rules, error mapping, test map |
| [`DECISION.md`](DECISION.md) | the most consequential choice, the main limitation, AI assistance |
| [`PRODUCTION_DESIGN.md`](PRODUCTION_DESIGN.md) | what would change at production scale |

## Prerequisites

- JDK 21, Maven 3.9+
- Python 3.11+
- `curl` (for the demo script)

## Start

```powershell
# terminal 1 - python service (offline model double by default, no API key needed)
cd python-service
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python -m uvicorn app.main:app --port 8000

# terminal 2 - spring service
cd spring-service
mvn spring-boot:run
```

## Tests

```powershell
cd python-service ; python -m pytest -q      # 37 tests
cd spring-service ; mvn test                 # 16 tests
```

Python tests cover policy outcomes, tenant/role access, effective dates, verbatim quotations,
ambiguous input, PDF extraction, prompt injection, and provider timeout / unavailable / malformed
output. Spring tests cover caller rejection, request validation, manifest validation, mixed batches
with duplicates and failures, and dependency failure mapping — all using a controllable
`FakePolicyEngineClient`, so no live dependency is needed.

## Demonstration

With both services running:

```powershell
powershell -File scripts\run-demo.ps1
```

This posts `examples/answer-request.json` to `/answer` and the supplied eight-document mixed batch
(including `request-02.pdf`) to `/batches`, and saves `examples/answer-response.json` and
`examples/batch-response.json`.

Manual equivalents:

```powershell
curl.exe -X POST http://localhost:8080/answer `
  -H "X-Caller-Id: atlas-employee-01" -H "Content-Type: application/json" `
  -d '{\"question\":\"What is my annual certification reimbursement limit?\",\"as_of\":\"2026-09-21\"}'

curl.exe -X POST http://localhost:8080/batches `
  -H "X-Caller-Id: atlas-employee-01" `
  -F "metadata=@examples/batch-request.json;type=application/json" `
  -F "files=@data/requests/request-01.txt" `
  -F "files=@data/requests/request-02.pdf"
```

## Public API

Both endpoints require `X-Caller-Id` (`atlas-employee-01`, `atlas-contractor-01`,
`boreal-employee-01`) and an `as_of` date in `YYYY-MM-DD`. Tenant and role come only from the caller
lookup — never from a request body, a document, or model output.

### POST /answer

Request: `{"question": "...", "as_of": "2026-09-21"}`

Response (HTTP 200):

```json
{
  "status": "ANSWERED",
  "answer": "Applicable policy for the caller on the requested date: ...",
  "citations": [{"chunk_id": "atlas-cert-current", "quote": "verbatim policy text"}]
}
```

| status | answer | citations |
| --- | --- | --- |
| `ANSWERED` | supported answer string | supporting eligible quotations |
| `INSUFFICIENT_EVIDENCE` | `null` | `[]` |
| `CONFLICT` | `null` | eligible quotations that disagree |

### POST /batches

`multipart/form-data` with one `metadata` part (JSON) and repeated `files` parts.

```json
{"batch_id": "demo-01", "as_of": "2026-09-21",
 "documents": [{"document_id": "request-01", "filename": "request-01.txt"}]}
```

Response (HTTP 200), results in manifest order:

```json
{
  "batch_id": "demo-01",
  "summary": {"total": 8, "completed": 7, "failed": 1},
  "results": [
    {
      "document_id": "request-01",
      "processing_status": "COMPLETED",
      "extracted": {"benefit": "certification", "amount": 18000.0, "currency": "INR", "reference": "CERT-101"},
      "field_evidence": {"benefit": "...", "amount": "...", "currency": "...", "reference": "..."},
      "policy": {"status": "ANSWERED", "answer": "...", "citations": [{"chunk_id": "...", "quote": "..."}]},
      "review_required": true,
      "issues": [{"code": "ANNUAL_LIMIT_NOT_A_BALANCE", "detail": "..."}],
      "duplicate_of": null,
      "error": null
    }
  ]
}
```

Chosen nested shapes:

- `extracted` — flat object; each unresolved or missing value is `null`.
- `field_evidence` — same keys as `extracted`; a verbatim source sentence for each supported value,
  `null` when the value is unresolved.
- `policy` — exactly the `/answer` response shape, or `null` when processing failed or the benefit
  could not be identified.
- `issues` — array of `{code, detail}`; `review_required` is always `true`.
- `error` — `null` on completion, otherwise `{code, message}`.
- On `FAILED`, `extracted`, `field_evidence` and `policy` are `null`; the item still appears.

Error responses use `{"error": {"code": "...", "message": "..."}}`.

### Error / issue codes

Batch-level (non-2xx, whole batch fails):

| code | HTTP | meaning |
| --- | --- | --- |
| `UNKNOWN_CALLER` | 401 | missing or unknown `X-Caller-Id` |
| `INVALID_QUESTION` | 400 | `/answer` question missing or blank |
| `INVALID_AS_OF` | 400 | missing or non `YYYY-MM-DD` date |
| `INVALID_METADATA` | 400 | metadata unparsable or incomplete |
| `DUPLICATE_MANIFEST_IDENTIFIER` | 400 | repeated `document_id` or `filename` |
| `BATCH_TOO_LARGE` | 400 | manifest exceeds `batch.max-documents` |
| `MISSING_FILE_PART` / `UNEXPECTED_FILE_PART` / `DUPLICATE_FILE_PART` | 400 | manifest and uploaded parts disagree |

Item-level `error.code` (item `FAILED`, other items continue):
`EMPTY_DOCUMENT`, `UNREADABLE_DOCUMENT`, `DOCUMENT_TOO_LARGE`, `FILE_READ_ERROR`, `MODEL_TIMEOUT`,
`MODEL_UNAVAILABLE`, `MODEL_MALFORMED_OUTPUT`, `ENGINE_TIMEOUT`, `ENGINE_UNAVAILABLE`,
`ENGINE_INVALID_RESPONSE`.
The same dependency failures on `/answer` return non-2xx (504 / 503 / 502) — never
`INSUFFICIENT_EVIDENCE`.

Review reason codes (`issues[].code`): `BENEFIT_NOT_IDENTIFIED`, `AMBIGUOUS_AMOUNT`,
`MISSING_AMOUNT`, `MISSING_REFERENCE`, `UNTRUSTED_INSTRUCTIONS_IGNORED`, `NO_APPLICABLE_POLICY`,
`POLICY_CONFLICT`, `POLICY_NOT_RETRIEVED`, `ANNUAL_LIMIT_NOT_A_BALANCE`,
`POLICY_CONDITIONS_NOT_VERIFIED`, `EXACT_DUPLICATE`, `HUMAN_REVIEW_REQUIRED`.

## Offline mode and configuration

The default model provider is `OfflineDeterministicProvider` — a deterministic double that composes
an answer only from the eligible passages handed to it. No API key or paid service is involved.

| setting | where | default |
| --- | --- | --- |
| `MODEL_PROVIDER_MODE` | python env | `offline` (`timeout`, `unavailable`, `malformed`, `fabricating` for demos) |
| `X-Model-Mode` header | python request | per-call override of the mode above; an unknown value is a 400, not a 500 |
| `POLICY_FILE` | python env | `data/policies.json` |
| `MAX_DOCUMENT_BYTES` | python env | `5242880` (the python service is separately addressable, so it enforces its own limit) |
| `policy-engine.base-url` | spring properties | `http://localhost:8000` |
| `policy-engine.connect-timeout-ms` / `read-timeout-ms` | spring properties | `2000` / `10000` |
| `batch.max-documents` | spring properties | `25` |

Spring config is bound to validated `@ConfigurationProperties` records, so a missing or
non-positive timeout fails startup rather than silently disabling the bound. The python service
does the same in `app/config.py`: every environment-backed setting is read and validated once at
import, and the effective values are logged at startup. Both services fail fast on bad config
rather than at the first request.

Configuration is read from the environment (12-factor); there is no `.env` file, and every
setting has a working default, so both services run with zero configuration.

One model attempt per question or item; no automatic retries anywhere.

## Evidence and safety rules

- Only `Approved` records for the caller's tenant and role whose interval covers `as_of` are
  eligible; the interval includes `effective_from` and excludes `effective_to`.
- Relevance is topic-based (certification, home-office, travel, training, wellness). A passage with
  no recognised topic — such as `atlas-injection-example` — is never retrieved, so ineligible text
  never reaches generation or the response.
- Model output is validated before use: it must be JSON, must contain a non-empty answer, and may
  only cite chunk IDs that were supplied. Anything else is a `MODEL_MALFORMED_OUTPUT` failure.
- Two eligible, relevant passages stating different amounts produce `CONFLICT`; no precedence rule
  is invented.
- Submitted documents are data. Identity claims and instruction-like text are recorded as
  `UNTRUSTED_INSTRUCTIONS_IGNORED` and change nothing.
- Nothing is approved or paid; `review_required` is always `true`.

## Traceability

Both services log at INFO with `batch_id` and `document_id`, plus benefit, policy status, issue
count, duplicate link and error code. Document text, policy text and model prompts are not logged.

## Not covered by tests

There is no automated test that runs Spring against the live Python service (the Spring tests use a
double, the Python tests call the service in-process), so a mismatch introduced in the HTTP
transport between them — for example a JSON field-naming change — would only be caught by running
`scripts\run-demo.ps1`. Concurrency behaviour under parallel batches is also untested.
