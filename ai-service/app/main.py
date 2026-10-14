import hmac
import logging
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, File, Header, Request, UploadFile
from fastapi.concurrency import run_in_threadpool
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .config import Settings, get_settings
from .errors import ApiError
from .extraction.extractor import OpenAIExtractor
from .logging_setup import log, request_id_var, setup_logging
from .ocr.registry import build_engine
from .ocr.service import OcrService
from .knowledge.service import KnowledgeService
from .schemas import EmbedRequest, EmbedResponse, ExplainRequest, ExplainResponse, ExtractRequest, ExtractResponse, OcrResponse

logger = logging.getLogger("ai-service")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    setup_logging(settings.log_level)
    app.state.ocr = OcrService(build_engine(settings), settings)
    app.state.extractor = OpenAIExtractor.from_settings(settings)
    app.state.knowledge = KnowledgeService(app.state.extractor.client, settings) if app.state.extractor else None
    log(
        logger,
        "ai-service started",
        ocr_engine=settings.ocr_engine,
        model=settings.openai_model,
        openai_configured=app.state.extractor is not None,
    )
    yield


app = FastAPI(title="InvoiceMind AI service", version="1.0.0", lifespan=lifespan)


# ------------------------------------------------------------ plumbing ----
@app.middleware("http")
async def request_context(request: Request, call_next):
    rid = request.headers.get("x-request-id") or uuid.uuid4().hex[:16]
    token = request_id_var.set(rid)
    started = time.perf_counter()
    status = 500
    try:
        response = await call_next(request)
        status = response.status_code
        response.headers["X-Request-ID"] = rid
        return response
    finally:
        log(
            logger,
            "request",
            method=request.method,
            path=request.url.path,
            status=status,
            duration_ms=int((time.perf_counter() - started) * 1000),
        )
        request_id_var.reset(token)


def _error(status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        content={"error": {"code": code, "message": message, "request_id": request_id_var.get()}},
    )


@app.exception_handler(ApiError)
async def api_error_handler(_: Request, exc: ApiError):
    return _error(exc.status, exc.code, exc.message)


@app.exception_handler(RequestValidationError)
async def validation_handler(_: Request, exc: RequestValidationError):
    # Do not echo the submitted body back (it may contain document text).
    fields = sorted({".".join(str(p) for p in e["loc"][1:]) or "body" for e in exc.errors()})
    return _error(422, "invalid_request", f"Invalid request fields: {', '.join(fields)}")


@app.exception_handler(Exception)
async def unhandled_handler(_: Request, exc: Exception):
    logger.exception("unhandled error")
    return _error(500, "internal_error", "Internal error")


def require_internal_token(
    x_internal_token: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
) -> None:
    if not x_internal_token or not hmac.compare_digest(x_internal_token, settings.ai_service_token):
        raise ApiError(401, "unauthorized", "Missing or invalid internal token")


# ------------------------------------------------------------ endpoints ----
@app.get("/health")
def health(request: Request, settings: Settings = Depends(get_settings)):
    engine = request.app.state.ocr.engine
    try:
        engine_version = engine.version()
    except Exception:  # binary missing: report, don't crash the probe
        engine_version = None
    return {
        "status": "ok" if engine_version else "degraded",
        "ocr_engine": engine.name,
        "ocr_engine_version": engine_version,
        "openai_configured": request.app.state.extractor is not None,
        "model": settings.openai_model,
    }


@app.post("/ocr", response_model=OcrResponse, dependencies=[Depends(require_internal_token)])
async def ocr(request: Request, file: UploadFile = File(...), settings: Settings = Depends(get_settings)):
    data = await file.read(settings.max_upload_bytes + 1)
    result = await run_in_threadpool(request.app.state.ocr.process, data)
    log(
        logger,
        "ocr done",
        bytes=len(data),
        mime=result.mime_type,
        pages=result.page_count,
        chars=len(result.text),
        confidence=result.confidence,
        methods=sorted({p.method for p in result.pages}),
    )
    return result


@app.post("/extract", response_model=ExtractResponse, dependencies=[Depends(require_internal_token)])
async def extract(body: ExtractRequest, request: Request):
    extractor: OpenAIExtractor | None = request.app.state.extractor
    if extractor is None:
        raise ApiError(503, "llm_not_configured", "OPENAI_API_KEY is not configured")
    return await run_in_threadpool(extractor.extract, body.text)


def _knowledge(request: Request) -> KnowledgeService:
    service: KnowledgeService | None = request.app.state.knowledge
    if service is None:
        raise ApiError(503, "llm_not_configured", "OPENAI_API_KEY is not configured")
    return service


@app.post("/embed", response_model=EmbedResponse, dependencies=[Depends(require_internal_token)])
async def embed(body: EmbedRequest, request: Request):
    return await run_in_threadpool(_knowledge(request).embed, body.texts)


@app.post("/explain", response_model=ExplainResponse, dependencies=[Depends(require_internal_token)])
async def explain(body: ExplainRequest, request: Request):
    return await run_in_threadpool(_knowledge(request).explain, body)
