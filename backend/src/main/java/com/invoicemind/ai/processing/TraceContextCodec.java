package com.invoicemind.ai.processing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Carries the W3C trace context of the request that queued a job across the database hop, so the extraction
 * calls made later by a worker thread still belong to the uploader's trace. A no-op when tracing is off.
 */
@Component
public class TraceContextCodec {
  private static final String HEADER = "traceparent";

  private final ObjectProvider<Tracer> tracer;
  private final ObjectProvider<Propagator> propagator;

  public TraceContextCodec(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
    this.tracer = tracer;
    this.propagator = propagator;
  }

  /** The current span as a {@code traceparent} value, or null. */
  public String current() {
    Tracer t = tracer.getIfAvailable();
    Propagator p = propagator.getIfAvailable();
    if (t == null || p == null) return null;
    TraceContext context = t.currentTraceContext().context();
    if (context == null) return null;
    Map<String, String> carrier = new HashMap<>();
    p.inject(context, carrier, Map::put);
    String value = carrier.get(HEADER);
    return value == null || value.length() > 100 ? null : value;
  }

  /** Runs {@code work} in a new span that continues {@code traceparent} (or starts a new trace). */
  public void runInSpan(String traceparent, String name, Runnable work) {
    Tracer t = tracer.getIfAvailable();
    Propagator p = propagator.getIfAvailable();
    if (t == null || p == null) {
      work.run();
      return;
    }
    Span span = traceparent == null
        ? t.nextSpan().name(name).start()
        : p.extract(Map.of(HEADER, traceparent), (carrier, key) -> carrier.get(key)).name(name).start();
    try (Tracer.SpanInScope ignored = t.withSpan(span)) {
      work.run();
    } catch (RuntimeException e) {
      span.error(e);
      throw e;
    } finally {
      span.end();
    }
  }
}
