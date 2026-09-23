# 03 — Low level design

Implements [02-hld.md](02-hld.md). Only the parts a reviewer needs to follow the code.

## 1. spring-service

### Packages

```
com.marlabs.desk
├── DeskApplication          @SpringBootApplication, @ConfigurationPropertiesScan
├── api/                     AnswerController, BatchController, ApiExceptionHandler
│   └── dto/                 one record per wire shape
├── batch/                   BatchService, BatchManifestValidator, BatchProperties
├── caller/                  Caller, CallerDirectory
├── engine/                  PolicyEngineClient, HttpPolicyEngineClient, PolicyEngineProperties
├── error/                   BadRequestException, EngineException, UnknownCallerException
└── support/                 AsOfDate
```

Dependencies point one way: `api → batch → engine`. Nothing below `api` imports it.

### Key classes

| class | does |
| --- | --- |
| `CallerDirectory` | `X-Caller-Id` → `Caller(tenant, role)`; throws `UnknownCallerException`. The only source of identity. |
| `AnswerController` | validates question and `as_of`, delegates, returns the engine's outcome unchanged. |
| `BatchManifestValidator` | all whole-batch 400 checks, before any item is processed. |
| `BatchService` | the per-item loop: hash, call engine, isolate failure, collect results. |
| `HttpPolicyEngineClient` | one bounded call per request; maps dependency errors to stable codes. |
| `ApiExceptionHandler` | the single place an exception becomes `{"error":{"code","message"}}` + status. |

### Batch item algorithm

```
for entry in manifest:                       # manifest order is preserved
    bytes   = read part
    digest  = sha256(bytes)
    dupOf   = first document_id already seen with this digest   # link, still process
    try:
        result = engine.process(tenant, role, as_of, bytes)
        status = COMPLETED
    except EngineException e:
        status = FAILED, error = e.code
    issues += EXACT_DUPLICATE if dupOf
    issues += HUMAN_REVIEW_REQUIRED
```

A duplicate is still processed; only the link is reported. Every entry yields exactly one result.

### Error mapping

| condition | code | status |
| --- | --- | --- |
| missing/unknown `X-Caller-Id` | `UNKNOWN_CALLER` | 401 |
| blank question | `INVALID_QUESTION` | 400 |
| `as_of` not `YYYY-MM-DD` | `INVALID_AS_OF` | 400 |
| manifest unusable / duplicate ids / part mismatch | `INVALID_METADATA`, `DUPLICATE_MANIFEST_IDENTIFIER`, `MISSING_FILE_PART`, `UNEXPECTED_FILE_PART`, `DUPLICATE_FILE_PART` | 400 |
| manifest over `batch.max-documents` | `BATCH_TOO_LARGE` | 400 |
| engine timeout / unavailable / bad response | `ENGINE_TIMEOUT`, `ENGINE_UNAVAILABLE`, `ENGINE_INVALID_RESPONSE` | 504 / 503 / 502 |

### Configuration

Bound to validated `@ConfigurationProperties` records, so a bad value fails startup.

| property | default |
| --- | --- |
| `policy-engine.base-url` | `http://localhost:8000` |
| `policy-engine.connect-timeout-ms` / `read-timeout-ms` | `2000` / `10000` |
| `batch.max-documents` | `25` |

---

## 2. python-service

### Packages

```
app/
├── main.py            routes, startup policy load, exception → HTTP status
├── config.py          every env-backed setting, validated once
├── models/            wire.py (public contract), internal.py
├── domain/            policies.py — corpus, eligibility, topic classification
├── providers/         model_provider.py — protocol + offline double + failure modes
└── services/          extraction_service.py, policy_service.py, review_service.py
```

Dependencies point one way: `main → services → domain / providers / models`.

### Eligibility (metadata only)

```python
record.tenant == tenant
and record.role == role
and record.approval_state == "Approved"
and record.effective_from <= as_of < record.effective_to   # end exclusive
```

Never parsed from passage text. A `Draft` record is never cited; a superseded record is invisible
once `as_of` reaches its `effective_to`.

### Relevance

`classify_benefit(text)` maps text to one of `certification`, `home-office`, `travel`, `training`,
`wellness` by keyword. A passage matching no topic is relevant to nothing — which is why the
prompt-injection example passage is structurally unreachable by generation.

### Status decision (`policy_service.answer_question`)

```
relevant = topic(question) ∩ eligible(tenant, role, as_of)
if not relevant:                    → INSUFFICIENT_EVIDENCE       (model not called)
if ≥2 distinct INR amounts:         → CONFLICT, cite all          (model not called)
otherwise:                          → generate, validate, ANSWERED
```

### Model output validation

Output is rejected unless **all** hold:

1. parses as JSON
2. is an object
3. `answer` is a non-empty string
4. `citation_ids` is a non-empty list
5. every id was in the passages we supplied

Citations are then rebuilt as `Citation(chunk_id, quote=our_record.text)` — the quote always comes
from our corpus, never from model output. Failure raises `MalformedModelOutput` → 502.

### Extraction

| field | rule |
| --- | --- |
| `benefit` | topic keyword match |
| `amount` / `currency` | `INR <number>`; **one distinct** value → extracted, **several** → `AMBIGUOUS_AMOUNT` and amount stays null, **none** → `MISSING_AMOUNT` |
| `reference` | `Reference: <ID>` |

Each extracted value carries the verbatim sentence it came from. Documents are bounded by
`MAX_DOCUMENT_BYTES` before parsing; empty or non-UTF-8 files and text-free PDFs are rejected.

### Review reasons (`review_service`)

Emitted alongside the policy answer, never instead of it:

`BENEFIT_NOT_IDENTIFIED`, `AMBIGUOUS_AMOUNT`, `MISSING_AMOUNT`, `MISSING_REFERENCE`,
`UNTRUSTED_INSTRUCTIONS_IGNORED`, `NO_APPLICABLE_POLICY`, `POLICY_CONFLICT`, `POLICY_NOT_RETRIEVED`,
`ANNUAL_LIMIT_NOT_A_BALANCE`, `POLICY_CONDITIONS_NOT_VERIFIED`, `HUMAN_REVIEW_REQUIRED`.

`ANNUAL_LIMIT_NOT_A_BALANCE` is emitted only when the cited policy actually states an amount — a
condition-only policy (for example "manager approval is required") does not claim a limit.

### Configuration

All settings are read and validated once in `config.py`; a bad value raises `ConfigError` at import.

| variable | default |
| --- | --- |
| `POLICY_FILE` | `data/policies.json` |
| `MAX_DOCUMENT_BYTES` | `5242880` |
| `MODEL_PROVIDER_MODE` | `offline` (`timeout`, `unavailable`, `malformed`, `fabricating` for demos) |

`X-Model-Mode` overrides the mode per request; an unknown value is a 400, not a 500.

---

## 3. Test map

| area | covers |
| --- | --- |
| `spring api/AnswerApiTest` | caller resolution, validation, dependency failures stay non-2xx |
| `spring api/BatchApiTest` | manifest validation, size cap, item isolation, duplicate linking |
| `python tests/api` | endpoint contract, startup load, unknown mode, oversized document |
| `python tests/domain` | corpus load failure, metadata-only eligibility |
| `python tests/services` | extraction rules, status decision, injection, model output validation |

`FabricatingProvider` — a double returning a valid-looking answer citing a chunk it was never
given — exists specifically to prove rule 5 of the output validation.
