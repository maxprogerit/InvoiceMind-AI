"""OpenTelemetry tracing (W3C trace context), off unless TRACING_ENABLED=true.

The backend sends a `traceparent` header with every call; FastAPI's instrumentation continues that trace, so one
upload shows up as a single trace across both services. httpx instrumentation adds the calls to OpenAI. The custom
spans below carry sizes, models and token counts - never document content.
"""
import logging

from opentelemetry import trace

from .config import Settings

logger = logging.getLogger(__name__)
tracer = trace.get_tracer("invoicemind.ai-service")


def setup_tracing(app, settings: Settings, extra_processors=()) -> bool:
    """Returns True when tracing was switched on. `extra_processors` lets tests attach an in-memory exporter."""
    if not settings.tracing_enabled:
        return False
    from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
    from opentelemetry.sdk.resources import Resource
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor

    provider = TracerProvider(resource=Resource.create({"service.name": "invoicemind-ai-service"}))
    if settings.otel_exporter_otlp_endpoint:
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter

        endpoint = settings.otel_exporter_otlp_endpoint.rstrip("/") + "/v1/traces"
        provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(endpoint=endpoint)))
    for processor in extra_processors:
        provider.add_span_processor(processor)
    trace.set_tracer_provider(provider)
    FastAPIInstrumentor.instrument_app(app, tracer_provider=provider, excluded_urls=".*/health$")
    HTTPXClientInstrumentor().instrument(tracer_provider=provider)
    logger.info("tracing enabled")
    return True


def current_trace_id() -> str | None:
    ctx = trace.get_current_span().get_span_context()
    return format(ctx.trace_id, "032x") if ctx.is_valid else None
