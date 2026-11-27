package com.invoicemind.ai.processing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.ai.AiClient;
import com.invoicemind.ai.ai.AiClientException;
import com.invoicemind.ai.ai.AiDtos.InvoiceResult;
import com.invoicemind.ai.ai.AiDtos.Extraction;
import com.invoicemind.ai.ai.AiDtos.OcrResponse;
import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.invoice.DocumentType;
import com.invoicemind.ai.invoice.InvoiceEntity;
import com.invoicemind.ai.invoice.InvoiceRepository;
import com.invoicemind.ai.invoice.InvoiceStatus;
import com.invoicemind.ai.invoice.LineItemEntity;
import com.invoicemind.ai.vendor.VendorEntity;
import com.invoicemind.ai.vendor.VendorService;
import com.invoicemind.ai.notification.NotificationService;
import com.invoicemind.ai.storage.FileStorage;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.firm.UserOrgAccessRepository;
import com.invoicemind.ai.validation.ValidationService;
import com.invoicemind.ai.webhook.WebhookOutbox;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The invoice pipeline: claim -> OCR -> LLM extraction -> deterministic validation.
 * Each stage commits in its own short transaction; no transaction is open during HTTP calls.
 * Everything coming back from the extract-service is treated as untrusted input. Driven by {@link ProcessingWorker}.
 */
@Slf4j
@Service
public class ProcessingService {
  static final class ProcessingException extends RuntimeException {
    ProcessingException(String message) { super(message); }
  }

  private static final int MAX_OCR_CHARS = 200_000;

  /** What the worker should do with the job after one attempt. */
  public record Outcome(Kind kind, String detail) {
    public enum Kind { DONE, RETRY, FAILED }

    static Outcome done() { return new Outcome(Kind.DONE, null); }
    static Outcome retry(String why) { return new Outcome(Kind.RETRY, why); }
    static Outcome failed(String why) { return new Outcome(Kind.FAILED, why); }
  }

  private final InvoiceRepository invoices;
  private final VendorService vendors;
  private final UserOrgAccessRepository access;
  private final FileStorage storage;
  private final AiClient ai;
  private final ValidationService validation;
  private final AuditService audit;
  private final StatusPublisher status;
  private final NotificationService notifications;
  private final WebhookOutbox webhooks;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final TransactionTemplate tx;

  public ProcessingService(InvoiceRepository invoices, VendorService vendors, UserOrgAccessRepository access,
                           FileStorage storage, AiClient ai, ValidationService validation, AuditService audit,
                           StatusPublisher status, NotificationService notifications, WebhookOutbox webhooks,
                           ObjectMapper mapper, Clock clock, PlatformTransactionManager txManager) {
    this.invoices = invoices;
    this.vendors = vendors;
    this.access = access;
    this.storage = storage;
    this.ai = ai;
    this.validation = validation;
    this.audit = audit;
    this.status = status;
    this.notifications = notifications;
    this.webhooks = webhooks;
    this.mapper = mapper;
    this.clock = clock;
    this.tx = new TransactionTemplate(txManager);
  }

  /**
   * One attempt at processing the invoice. Idempotent: an invoice that is not waiting (already processed,
   * deleted, or retried by hand) is skipped. Transient failures of the AI service (timeouts, 5xx) are retried
   * by the job queue with backoff unless this is the last attempt; anything else fails the invoice at once.
   */
  public Outcome process(UUID invoiceId, boolean lastAttempt) {
    Integer claimed = tx.execute(s -> invoices.claimForProcessing(invoiceId, Instant.now(clock)));
    if (claimed == null || claimed == 0) {
      log.info("Invoice {} is not waiting for processing, skipping", invoiceId);
      return Outcome.done();
    }
    InvoiceHandle handle = tx.execute(s -> {
      InvoiceEntity i = invoices.findById(invoiceId).orElseThrow();
      audit.log(i.getOrganizationId(), null, "PROCESSING_STARTED", AuditService.INVOICE, i.getId(), null);
      status.publish(i);
      return new InvoiceHandle(i.getStorageKey(), i.getOriginalFilename(), i.getOrganizationId(), i.getFileSha256(),
          i.getCreatedAt());
    });

    try {
      // File-level duplicate: an identical file that was already extracted is never sent to the model again.
      Reuse reuse = tx.execute(s -> findReusable(invoiceId, handle));
      if (reuse != null && reuse.source() != null) {
        storeExtraction(invoiceId, reuse.source());
        markReused(invoiceId, reuse.sourceId());
        finishValidation(invoiceId);
        return Outcome.done();
      }
      if (reuse != null && reuse.waitForEarlier() && !lastAttempt) {
        log.info("Invoice {}: an identical file is still being processed, waiting for its result", invoiceId);
        resetForRetry(invoiceId);
        return Outcome.retry("waiting for the identical file " + reuse.sourceId());
      }

      byte[] file = storage.read(handle.storageKey());
      InvoiceResult result = ai.processInvoice(file, handle.filename());
      if (result.ocr() == null || result.ocr().text() == null || result.ocr().text().isBlank()) {
        throw new ProcessingException("No text could be read from the document");
      }
      if (result.extraction() == null) throw new ProcessingException("The extraction service returned no extraction");
      storeExtraction(invoiceId, result);
      finishValidation(invoiceId);
      return Outcome.done();
    } catch (AiClientException e) {
      if (e.retryable() && !lastAttempt) {
        log.warn("Transient AI service failure for invoice {} ({}), will retry", invoiceId, e.code());
        resetForRetry(invoiceId);
        return Outcome.retry(e.code() + ": " + e.getMessage());
      }
      fail(invoiceId, e.getMessage(), e.code());
      return Outcome.failed(e.code());
    } catch (ProcessingException e) {
      fail(invoiceId, e.getMessage(), "processing_error");
      return Outcome.failed("processing_error");
    } catch (RuntimeException e) {
      log.error("Unexpected failure while processing invoice {}", invoiceId, e);
      fail(invoiceId, "Unexpected processing error", "internal_error");
      return Outcome.failed("internal_error");
    }
  }

  /** Puts an invoice that was being processed back to UPLOADED so the next attempt can claim it. */
  public void resetForRetry(UUID invoiceId) {
    tx.executeWithoutResult(s -> invoices.findById(invoiceId).ifPresent(inv -> {
      if (inv.getStatus() == InvoiceStatus.PROCESSING) {
        inv.setStatus(InvoiceStatus.UPLOADED);
        invoices.saveAndFlush(inv);
        status.publish(inv);
      }
    }));
  }

  private record InvoiceHandle(String storageKey, String filename, UUID organizationId, String sha256, Instant createdAt) {}

  /** {@code source} set: reuse that result; {@code waitForEarlier}: the identical file is still in flight. */
  private record Reuse(UUID sourceId, InvoiceResult source, boolean waitForEarlier) {}

  private static final java.util.Set<InvoiceStatus> REUSABLE = java.util.EnumSet.of(InvoiceStatus.EXTRACTED,
      InvoiceStatus.NEEDS_REVIEW, InvoiceStatus.VALIDATED, InvoiceStatus.APPROVED, InvoiceStatus.REJECTED);
  private static final java.util.Set<InvoiceStatus> IN_FLIGHT = java.util.EnumSet.of(InvoiceStatus.UPLOADED,
      InvoiceStatus.PROCESSING);

  private Reuse findReusable(UUID invoiceId, InvoiceHandle h) {
    if (h.sha256() == null) return null;
    for (InvoiceEntity earlier : invoices.findEarlierSameFile(h.organizationId(), invoiceId, h.sha256(), REUSABLE, h.createdAt())) {
      if (earlier.getExtractionJson() != null && earlier.getOcrText() != null) {
        return new Reuse(earlier.getId(), storedResult(earlier), false);
      }
    }
    return invoices.findEarlierSameFile(h.organizationId(), invoiceId, h.sha256(), IN_FLIGHT, h.createdAt()).stream()
        .findFirst().map(e -> new Reuse(e.getId(), null, true)).orElse(null);
  }

  /** The earlier invoice's stored model output, as if the service had just returned it (no tokens, no cost). */
  private InvoiceResult storedResult(InvoiceEntity source) {
    var ocr = new OcrResponse(source.getOcrText(), List.of(), source.getOcrPageCount() == null ? 0 : source.getOcrPageCount(),
        source.getOcrConfidence() == null ? 0 : source.getOcrConfidence().doubleValue(), source.getOcrEngine(),
        source.getContentType());
    Extraction extraction = mapper.convertValue(source.getExtractionJson(), Extraction.class);
    var validation = source.getExtractValidation() == null ? null
        : mapper.convertValue(source.getExtractValidation(), com.invoicemind.ai.ai.AiDtos.ExtractValidation.class);
    var usage = new com.invoicemind.ai.ai.AiDtos.Usage(source.getLlmModel(), 0, 0, BigDecimal.ZERO, 0);
    return new InvoiceResult(source.getTextSource(), ocr, extraction, validation, source.isInjectionSuspected(), usage);
  }

  private void markReused(UUID invoiceId, UUID sourceId) {
    tx.executeWithoutResult(s -> invoices.findById(invoiceId).ifPresent(inv -> {
      inv.setReusedFrom(sourceId);
      invoices.saveAndFlush(inv);
      audit.log(inv.getOrganizationId(), null, "EXTRACTION_REUSED", AuditService.INVOICE, inv.getId(),
          Map.of("from", sourceId.toString(), "reason", "identical file (same SHA-256), model not called"));
    }));
  }

  // ------------------------------------------------------------- stage 1
  private void storeExtraction(UUID id, InvoiceResult response) {
    OcrResponse ocr = response.ocr();
    runWithVendorRetry(() -> tx.executeWithoutResult(s -> {
      InvoiceEntity inv = invoices.findById(id).orElseThrow(() -> new ProcessingException("Invoice was deleted"));
      Extraction e = response.extraction();

      inv.setOcrText(truncate(ocr.text(), MAX_OCR_CHARS));
      inv.setOcrEngine(ocr.engine());
      inv.setOcrConfidence(BigDecimal.valueOf(clamp01(ocr.confidence())).setScale(4, RoundingMode.HALF_UP));
      inv.setOcrPageCount(ocr.pageCount());
      inv.setTextSource(textSource(response.source()));
      inv.setExtractValidation(response.validation() == null ? null
          : mapper.convertValue(response.validation(), new TypeReference<Map<String, Object>>() {}));

      DocumentType type = DocumentType.parse(e.documentType());
      inv.setDocumentType(type);
      String vendorName = trimToNull(e.vendor(), 300);
      inv.setVendorName(vendorName);
      inv.setVendorTaxId(VendorEntity.normalizeTaxId(e.vendorTaxId()));
      VendorService.Match vendor = vendors.match(inv.getOrganizationId(), vendorName, e.vendorTaxId(), currency(e.currency()));
      inv.setVendorId(vendor == null ? null : vendor.vendorId());
      inv.setInvoiceNumber(trimToNull(e.invoiceNumber(), 100));
      inv.setInvoiceDate(parseDate(e.invoiceDate()));
      inv.setDueDate(parseDate(e.dueDate()));
      inv.setCurrency(currency(e.currency()));
      inv.setSubtotal(type.signed(money(e.subtotal())));
      inv.setTax(type.signed(money(e.tax())));
      inv.setTotal(type.signed(money(e.total())));
      inv.replaceLineItems(lineItems(e, type));

      inv.setFieldConfidence(confidenceMap(e));
      inv.setModelNeedsReview(e.needsReview());
      inv.setModelReviewReasons(e.reviewReasons() == null ? List.of() : e.reviewReasons().stream().limit(10)
          .map(r -> truncate(r, 200)).toList());
      inv.setInjectionSuspected(response.injectionSuspected());
      inv.setExtractionJson(mapper.convertValue(e, new TypeReference<Map<String, Object>>() {}));
      if (response.usage() != null) {
        inv.setLlmModel(truncate(response.usage().model(), 100));
        inv.setInputTokens(response.usage().inputTokens());
        inv.setOutputTokens(response.usage().outputTokens());
        inv.setCostUsd(response.usage().costUsd() == null ? null : response.usage().costUsd().setScale(6, RoundingMode.HALF_UP));
      }
      inv.setStatus(InvoiceStatus.EXTRACTED);
      invoices.saveAndFlush(inv);
      audit.log(inv.getOrganizationId(), null, "EXTRACTION_COMPLETED", AuditService.INVOICE, inv.getId(),
          Map.of("model", String.valueOf(inv.getLlmModel()), "inputTokens", String.valueOf(inv.getInputTokens()),
              "outputTokens", String.valueOf(inv.getOutputTokens())));
      status.publish(inv);
    }));
  }

  // ------------------------------------------------------------- stage 2
  private void finishValidation(UUID id) {
    tx.executeWithoutResult(s -> {
      InvoiceEntity inv = invoices.findById(id).orElseThrow(() -> new ProcessingException("Invoice was deleted"));
      InvoiceStatus next = validation.revalidate(inv);
      inv.setStatus(next);
      inv.setWasFlagged(next == InvoiceStatus.NEEDS_REVIEW);
      Instant now = Instant.now(clock);
      inv.setProcessingFinishedAt(now);
      if (inv.getProcessingStartedAt() != null) {
        inv.setProcessingMs(Duration.between(inv.getProcessingStartedAt(), now).toMillis());
      }
      invoices.saveAndFlush(inv);

      long errors = inv.getIssues().stream().filter(i -> "ERROR".equals(i.getSeverity())).count();
      audit.log(inv.getOrganizationId(), null, "VALIDATION_COMPLETED", AuditService.INVOICE, inv.getId(),
          Map.of("status", next.name(), "errors", errors, "issues", inv.getIssues().size()));
      webhooks.invoiceEvent(next == InvoiceStatus.NEEDS_REVIEW ? "invoice.needs_review" : "invoice.validated", inv);
      notifyAfterProcessing(inv, next);
      status.publish(inv);
      validation.recheckNewerOfSameVendor(inv).forEach(status::publish);
    });
  }

  private void notifyAfterProcessing(InvoiceEntity inv, InvoiceStatus next) {
    String label = label(inv);
    if (next == InvoiceStatus.NEEDS_REVIEW) {
      notifications.notify(inv.getOrganizationId(), inv.getUploadedBy(), "NEEDS_REVIEW", "Invoice needs review",
          label + " was extracted but needs a human check.", inv.getId());
    } else {
      notifications.notify(inv.getOrganizationId(), inv.getUploadedBy(), "PROCESSED", "Invoice processed",
          label + " passed validation.", inv.getId());
      for (UserEntity reviewer : access.findEnabledUsersWithRoleIn(inv.getOrganizationId(),
          List.of(Role.APPROVER, Role.ADMIN))) {
        if (!reviewer.getId().equals(inv.getUploadedBy())) {
          notifications.notify(inv.getOrganizationId(), reviewer.getId(), "AWAITING_APPROVAL",
              "Invoice awaiting approval", label + " is ready for your decision.", inv.getId());
        }
      }
    }
  }

  // ------------------------------------------------------------- failure
  public void fail(UUID id, String reason, String code) {
    tx.executeWithoutResult(s -> invoices.findById(id).ifPresent(inv -> {
      if (inv.getStatus() != InvoiceStatus.PROCESSING && inv.getStatus() != InvoiceStatus.UPLOADED
          && inv.getStatus() != InvoiceStatus.EXTRACTED) return;
      Instant now = Instant.now(clock);
      inv.setStatus(InvoiceStatus.FAILED);
      inv.setFailureReason(truncate(reason, 500));
      inv.setProcessingFinishedAt(now);
      if (inv.getProcessingStartedAt() != null) {
        inv.setProcessingMs(Duration.between(inv.getProcessingStartedAt(), now).toMillis());
      }
      invoices.saveAndFlush(inv);
      audit.log(inv.getOrganizationId(), null, "PROCESSING_FAILED", AuditService.INVOICE, inv.getId(),
          Map.of("code", code, "reason", truncate(reason, 200)));
      notifications.notify(inv.getOrganizationId(), inv.getUploadedBy(), "FAILED", "Processing failed",
          label(inv) + ": " + truncate(reason, 200), inv.getId());
      webhooks.invoiceEvent("invoice.failed", inv);
      status.publish(inv);
    }));
  }

  // ------------------------------------------------------------- helpers
  /** Only the three documented values are stored; anything else from the service is dropped. */
  static String textSource(String source) {
    return source != null && java.util.Set.of("text_layer", "ocr", "mixed").contains(source) ? source : null;
  }

  /** Two invoices for a brand-new vendor processed concurrently race on the unique index; retry once. */
  private void runWithVendorRetry(Runnable work) {
    try {
      work.run();
    } catch (DataIntegrityViolationException e) {
      log.info("Vendor insert raced, retrying once");
      work.run();
    }
  }

  private List<LineItemEntity> lineItems(Extraction e, DocumentType type) {
    List<LineItemEntity> items = new ArrayList<>();
    if (e.lineItems() == null) return items;
    for (var li : e.lineItems().stream().limit(500).toList()) {
      LineItemEntity item = new LineItemEntity();
      String description = trimToNull(li.description(), 1000);
      item.setDescription(description == null ? "(no description)" : description);
      item.setQuantity(type.signedQuantity(li.quantity() == null ? null : li.quantity().setScale(4, RoundingMode.HALF_UP)));
      item.setUnitPrice(type.signed(li.unitPrice() == null ? null : li.unitPrice().setScale(4, RoundingMode.HALF_UP)));
      item.setTotal(type.signed(money(li.total())));
      items.add(item);
    }
    return items;
  }

  private static Map<String, Double> confidenceMap(Extraction e) {
    Map<String, Double> map = new LinkedHashMap<>();
    var c = e.confidence();
    if (c == null) return map;
    map.put("vendor", clamp01(c.vendor()));
    map.put("invoiceNumber", clamp01(c.invoiceNumber()));
    map.put("invoiceDate", clamp01(c.invoiceDate()));
    map.put("dueDate", clamp01(c.dueDate()));
    map.put("currency", clamp01(c.currency()));
    map.put("lineItems", clamp01(c.lineItems()));
    map.put("subtotal", clamp01(c.subtotal()));
    map.put("tax", clamp01(c.tax()));
    map.put("total", clamp01(c.total()));
    return map;
  }

  private static double clamp01(double v) { return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v)); }

  private static BigDecimal money(BigDecimal v) {
    if (v == null) return null;
    BigDecimal scaled = v.setScale(2, RoundingMode.HALF_UP);
    // numeric(14,2) holds < 10^12; anything larger is a misread, not a number to store
    return scaled.abs().compareTo(new BigDecimal("999999999999.99")) > 0 ? null : scaled;
  }

  private static LocalDate parseDate(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return LocalDate.parse(s.trim());
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String currency(String s) {
    if (s == null) return null;
    String c = s.trim().toUpperCase(Locale.ROOT);
    return c.matches("[A-Z]{3}") ? c : null;
  }

  private static String trimToNull(String s, int max) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : truncate(t, max);
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }

  private static String label(InvoiceEntity inv) { return inv.displayLabel(); }
}
