"""Tracing: the ai-service continues the caller's W3C trace and describes its work without leaking content."""
import io

import pytest
from fastapi import FastAPI
from opentelemetry.trace import SpanKind
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from app.config import Settings
from app.tracing import setup_tracing

from .conftest import HEADERS, INVOICE_TEXT, make_extraction, make_response

TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
TRACEPARENT = f"00-{TRACE_ID}-00f067aa0ba902b7-01"


def tiny_pdf() -> bytes:
    from reportlab.pdfgen import canvas

    buf = io.BytesIO()
    c = canvas.Canvas(buf)
    c.drawString(72, 720, "ACME GmbH Invoice INV-0042 Total EUR 1,190.00 - enough text for a text layer to be used")
    c.save()
    return buf.getvalue()


@pytest.fixture(scope="module")
def _tracing():
    """Tracing switched on once for this module (the global tracer binds to the first provider it sees)."""
    from app.main import app

    exporter = InMemorySpanExporter()
    app.middleware_stack = None  # earlier tests already built the stack; let instrumentation join a fresh one
    enabled = setup_tracing(app, Settings(ai_service_token="test-token", tracing_enabled=True), [SimpleSpanProcessor(exporter)])
    assert enabled
    from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor

    # the TestClient is httpx too: it must not start spans of its own and rewrite the traceparent under test
    HTTPXClientInstrumentor().uninstrument()
    yield exporter
    FastAPIInstrumentor.uninstrument_app(app)


@pytest.fixture
def spans(_tracing):
    _tracing.clear()
    return _tracing


def test_tracing_is_off_by_default():
    assert setup_tracing(FastAPI(), Settings(ai_service_token="test-token")) is False


def test_extract_continues_the_callers_trace_and_records_model_usage(spans, client, with_model):
    with_model(lambda kw: make_response(make_extraction(), input_tokens=1000, output_tokens=200))
    resp = client.post("/extract", json={"text": INVOICE_TEXT}, headers={**HEADERS, "traceparent": TRACEPARENT})
    assert resp.status_code == 200

    finished = {s.name: s for s in spans.get_finished_spans()}
    server = next(s for s in spans.get_finished_spans() if s.kind == SpanKind.SERVER and s.name == "POST /extract")
    assert format(server.context.trace_id, "032x") == TRACE_ID  # the same trace the backend started
    assert format(server.parent.span_id, "016x") == "00f067aa0ba902b7"

    llm = finished["llm.extract"]
    assert format(llm.context.trace_id, "032x") == TRACE_ID
    assert llm.attributes["llm.input_tokens"] == 1000 and llm.attributes["llm.output_tokens"] == 200
    assert llm.attributes["llm.model"] and llm.attributes["extraction.needs_review"] is False


def test_spans_never_contain_document_content(spans, client, with_model):
    with_model(lambda kw: make_response(make_extraction()))
    client.post("/extract", json={"text": INVOICE_TEXT}, headers=HEADERS)
    client.post("/ocr", files={"file": ("x.pdf", tiny_pdf(), "application/pdf")}, headers=HEADERS)
    dump = " ".join(str(dict(s.attributes or {})) for s in spans.get_finished_spans())
    assert "ACME" not in dump and "INV-0042" not in dump and "1,190" not in dump
    ocr = next(s for s in spans.get_finished_spans() if s.name == "ocr.process")
    assert ocr.attributes["ocr.pages"] == 1 and ocr.attributes["ocr.engine"] == "tesseract"


def test_health_checks_are_not_traced(spans, client):
    client.get("/health")
    assert [s.name for s in spans.get_finished_spans() if "health" in s.name] == []
