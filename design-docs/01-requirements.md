# 01 — Requirements

Status: agreed | Owner: engineering | Source: product brief

## 1. Problem

Employees submit reimbursement and benefit requests as free-text documents. A support desk answers
policy questions and triages those requests by hand. The desk is slow, and answers vary between
agents because policies differ by tenant, by role, and by date.

We want a service that answers policy questions **with citations** and triages submitted documents,
without ever deciding an outcome for the employee.

## 2. Actors

| actor | interacts how |
| --- | --- |
| Employee / contractor | indirectly — their request document is submitted on their behalf |
| Internal caller system | calls the public API with a caller identity header |
| Reviewer | reads the output and makes the actual decision |

## 3. Functional requirements

| id | requirement |
| --- | --- |
| FR-1 | Answer a policy question for a caller as of a given date, quoting the policy text it relied on. |
| FR-2 | Return a citation for every answer. An answer with no supporting policy must not be produced. |
| FR-3 | Say "insufficient evidence" when no approved policy applies, rather than guessing. |
| FR-4 | Report a conflict when two approved policies apply simultaneously and disagree. Do not pick a winner. |
| FR-5 | Accept a batch of request documents with a manifest and return one result per manifest entry. |
| FR-6 | Extract the benefit, amount, currency and reference from each document, with a verbatim quote supporting each value. |
| FR-7 | Flag every result for human review. The service never approves, never computes a payable amount, never contacts the employee. |
| FR-8 | Isolate per-item failures: one bad document must not fail the rest of the batch. |
| FR-9 | Detect byte-identical duplicate documents within a batch and link them. |
| FR-10 | Support PDF and plain-text documents. |

## 4. Rules the system must enforce

| id | rule |
| --- | --- |
| BR-1 | **Identity is server-derived.** Tenant and role come from the caller registry, never from the request body or the document text. |
| BR-2 | **Policies are scoped.** A policy applies only if tenant, role and approval state match and `effective_from <= as_of < effective_to`. |
| BR-3 | **Documents are data, not instructions.** Instruction-like text in a document is recorded as a review reason and changes nothing. |
| BR-4 | **An annual limit is not a balance.** Citing a limit must not imply the amount is payable. |
| BR-5 | **Dependency failures are not business outcomes.** A model or downstream failure must never be reported as "insufficient evidence". |

## 5. Non-functional requirements

| id | requirement |
| --- | --- |
| NFR-1 | Runs offline — no paid service or API key needed to run or test. |
| NFR-2 | Deterministic and reproducible: the same input yields the same status and citations. |
| NFR-3 | All outbound calls bounded by a timeout. One attempt, no hidden retries. |
| NFR-4 | Logs carry identifiers and codes only — never document or policy text. |
| NFR-5 | Bad configuration fails at startup, not on the first request. |

## 6. Service split

Two services, because the two concerns fail and change for different reasons.

| service | owns | does **not** own |
| --- | --- | --- |
| **spring-service** (public API, :8080) | the public contract, caller identity, request validity, batch orchestration, HTTP error shaping | any policy logic, any text handling |
| **python-service** (:8000) | document text extraction, policy retrieval, eligibility, conflict detection, answer generation and validation | who the caller is, what the public contract looks like |

The public API never exposes the python service, and the python service never decides identity.

## 7. Out of scope

Persistence, queues, authentication beyond a caller header, OCR for scanned PDFs, a real model
provider, a frontend, approval-system integration, concurrent batch processing.

## 8. Acceptance criteria

- A question with an applicable approved policy returns `ANSWERED` with a verbatim citation.
- A question with no applicable policy returns `INSUFFICIENT_EVIDENCE` and no citation.
- Two applicable approved policies stating different amounts return `CONFLICT` citing both.
- A document containing injected instructions is answered from the caller's real tenant and role,
  and carries an `UNTRUSTED_INSTRUCTIONS_IGNORED` review reason.
- A batch of eight documents including one empty file returns eight results: seven `COMPLETED`,
  one `FAILED`.
- A model timeout returns a non-2xx status, never `INSUFFICIENT_EVIDENCE`.
- Every result carries `review_required: true`.
