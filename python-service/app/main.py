"""FastAPI application: document extraction, policy retrieval, answer generation."""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from datetime import date
from typing import Optional

from fastapi import FastAPI, File, Form, Header, Request, UploadFile
from fastapi.responses import JSONResponse

from . import config
from .services.extraction_service import DocumentError
from .providers.model_provider import (
    ProviderTimeout,
    ProviderUnavailable,
    UnknownProviderMode,
    get_provider,
)
from .models.wire import AnswerRequest, AnswerResponse, DocumentResult
from .domain.policies import load_policies
from .services.policy_service import MalformedModelOutput, answer_question
from .services.review_service import process_document

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("policy-desk")


@asynccontextmanager
async def lifespan(_: FastAPI):
    # Load the corpus once at startup so a missing or malformed policy file aborts the boot
    # instead of turning into a 500 on the first real request.
    records = load_policies()
    logger.info(
        "startup policies=%d policy_file=%s max_document_bytes=%d model_provider_mode=%s",
        len(records),
        config.POLICY_FILE,
        config.MAX_DOCUMENT_BYTES,
        config.MODEL_PROVIDER_MODE,
    )
    yield


app = FastAPI(title="Policy desk python service", lifespan=lifespan)


def _error(status_code: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"error_code": code, "message": message})


@app.exception_handler(UnknownProviderMode)
async def _unknown_mode(_: Request, exc: UnknownProviderMode) -> JSONResponse:
    # A bad X-Model-Mode is a caller mistake, so it must not surface as a 500.
    logger.warning("unknown_model_mode %s", exc)
    return _error(400, "UNKNOWN_MODEL_MODE", "Requested model mode is not supported.")


@app.exception_handler(ProviderTimeout)
async def _timeout(_: Request, exc: ProviderTimeout) -> JSONResponse:
    logger.warning("model_timeout")
    return _error(504, "MODEL_TIMEOUT", "Model provider timed out.")


@app.exception_handler(ProviderUnavailable)
async def _unavailable(_: Request, exc: ProviderUnavailable) -> JSONResponse:
    logger.warning("model_unavailable")
    return _error(503, "MODEL_UNAVAILABLE", "Model provider is unavailable.")


@app.exception_handler(MalformedModelOutput)
async def _malformed(_: Request, exc: MalformedModelOutput) -> JSONResponse:
    logger.warning("model_malformed_output reason=%s", exc)
    return _error(502, "MODEL_MALFORMED_OUTPUT", "Model returned unusable output.")


@app.exception_handler(DocumentError)
async def _document(_: Request, exc: DocumentError) -> JSONResponse:
    logger.info("document_error code=%s", exc.code)
    return _error(422, exc.code, exc.message)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/answer", response_model=AnswerResponse)
def answer(req: AnswerRequest, x_model_mode: Optional[str] = Header(default=None)) -> AnswerResponse:
    logger.info("answer tenant=%s role=%s as_of=%s", req.tenant, req.role, req.as_of)
    result = answer_question(
        req.tenant, req.role, req.as_of, req.question, provider=get_provider(x_model_mode)
    )
    logger.info("answer_result status=%s citations=%d", result.status, len(result.citations))
    return result


@app.post("/documents/process", response_model=DocumentResult)
async def process(
    tenant: str = Form(...),
    role: str = Form(...),
    as_of: date = Form(...),
    batch_id: str = Form(default="-"),
    document_id: str = Form(default="-"),
    file: UploadFile = File(...),
    x_model_mode: Optional[str] = Header(default=None),
) -> DocumentResult:
    content = await file.read()
    logger.info(
        "process batch_id=%s document_id=%s filename=%s bytes=%d",
        batch_id,
        document_id,
        file.filename,
        len(content),
    )
    result = process_document(
        tenant,
        role,
        as_of,
        file.filename or "",
        content,
        provider=get_provider(x_model_mode),
    )
    logger.info(
        "process_result batch_id=%s document_id=%s benefit=%s policy_status=%s issues=%d",
        batch_id,
        document_id,
        result.extracted.benefit,
        result.policy.status if result.policy else None,
        len(result.issues),
    )
    return result
