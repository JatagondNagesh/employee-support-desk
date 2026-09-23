# Decision note

## Consequential design choice: status is decided by code, the model only phrases the answer

Eligibility (tenant, role, approval state, effective interval), topic relevance, and conflict
detection are deterministic Python functions. The model provider is called only after the evidence
set is already fixed, and its output is validated: it must be JSON, must contain a non-empty answer,
and may only cite chunk IDs that were passed to it. `INSUFFICIENT_EVIDENCE` and `CONFLICT` are
therefore reached without ever calling the model.

This makes the three statuses reproducible and testable, keeps the prompt-injection passage and
out-of-tenant records out of generation entirely, and means a swap to a real LLM cannot change
access decisions — only wording.

**Alternative rejected:** give the model all eligible passages and let it report
`ANSWERED` / `INSUFFICIENT_EVIDENCE` / `CONFLICT` itself. It is less code and handles unforeseen
phrasings better, but it makes access and conflict behaviour non-deterministic, untestable without
a live provider, and dependent on the model resisting injected instructions.

## Main limitation

Retrieval is keyword-based topic matching (`policies.BENEFIT_KEYWORDS`). Any new benefit or an
unusual phrasing of an existing one falls outside the map and yields `INSUFFICIENT_EVIDENCE` /
`BENEFIT_NOT_IDENTIFIED` rather than a wrong answer — safe, but under-retrieving. It also means
conflict detection only compares INR amounts within one topic; two passages that contradict each
other in words rather than numbers would both be cited as a single `ANSWERED` result.

Extraction is similarly literal: it recognises `INR <amount>` and `Reference: <ID>` only, and a
scanned (image-only) PDF is reported as `UNREADABLE_DOCUMENT` because no OCR is used.

## Time spent and unfinished work

Roughly half a day end to end.

Unfinished / deliberately out of scope: no cross-service integration test (Spring tests use a
double); no real model integration; no persistence, queue, OCR, authentication or frontend; batch
items are processed sequentially; the caller directory is an in-memory constant map.

## AI assistance

An AI coding assistant was used to draft boilerplate (POM, DTO records, test scaffolding) and the
first pass of this documentation. The service decomposition, evidence and eligibility rules, status
contract, failure taxonomy and test cases were specified and reviewed by me, and every behaviour
described here was verified by running the tests and `scripts\run-demo.ps1`.
