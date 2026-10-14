package com.invoicemind.ai.invoice;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.audit.AuditService.AuditEntryDto;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.common.PageResponse;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.invoice.InvoiceDtos.DecisionRequest;
import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceDetail;
import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceSummary;
import com.invoicemind.ai.invoice.InvoiceDtos.LineItemDto;
import com.invoicemind.ai.invoice.InvoiceDtos.OcrTextResponse;
import com.invoicemind.ai.invoice.InvoiceDtos.UpdateInvoiceRequest;
import com.invoicemind.ai.notification.NotificationService;
import com.invoicemind.ai.processing.ProcessingQueue.InvoiceQueuedEvent;
import com.invoicemind.ai.processing.StatusPublisher;
import com.invoicemind.ai.ratelimit.RateLimiter;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.storage.FileStorage;
import com.invoicemind.ai.storage.FileValidator;
import com.invoicemind.ai.storage.FileValidator.ValidatedFile;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.validation.ValidationService;
import com.invoicemind.ai.webhook.WebhookOutbox;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Invoice use cases. Every read and write goes through {@link #load}, which is the single place that
 * enforces tenant isolation (organisation from the JWT) and USER-role ownership.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {
  private static final Set<String> SORTABLE = Set.of("createdAt", "invoiceDate", "dueDate", "total", "vendorName",
      "invoiceNumber", "status");

  private final InvoiceRepository invoices;
  private final InvoiceMapper mapper;
  private final FileValidator fileValidator;
  private final FileStorage storage;
  private final ValidationService validation;
  private final VendorService vendors;
  private final AuditService audit;
  private final StatusPublisher status;
  private final NotificationService notifications;
  private final WebhookOutbox webhooks;
  private final RateLimiter rateLimiter;
  private final ApplicationEventPublisher events;
  private final AppProperties props;
  private final Clock clock;

  // ------------------------------------------------------------------ upload
  @Transactional // the delegate below is a self-call, so this method must open the transaction itself
  public InvoiceSummary upload(MultipartFile file, AuthPrincipal me) { return upload(file, me, "web"); }

  /** {@code source} ("web", "email") is recorded in the audit trail so it is clear how a document arrived. */
  @Transactional
  public InvoiceSummary upload(MultipartFile file, AuthPrincipal me, String source) {
    rateLimiter.check("upload", me.userId().toString(), props.rateLimit().uploadPerMinute());
    ValidatedFile vf = fileValidator.validate(file);

    InvoiceEntity inv = new InvoiceEntity();
    inv.setOrganizationId(me.organizationId());
    inv.setUploadedBy(me.userId());
    inv.setOriginalFilename(vf.safeName());
    inv.setStorageKey("pending");
    inv.setContentType(vf.contentType());
    inv.setFileSize(vf.data().length);
    inv.setFileSha256(vf.sha256());
    inv = invoices.saveAndFlush(inv);
    inv.setStorageKey(storage.store(me.organizationId(), inv.getId(), vf.extension(), vf.data()));

    audit.log(me.organizationId(), me.userId(), "INVOICE_UPLOADED", AuditService.INVOICE, inv.getId(),
        Map.of("filename", vf.safeName(), "bytes", vf.data().length, "sha256", vf.sha256().substring(0, 16), "source", source));
    status.publish(inv);
    events.publishEvent(new InvoiceQueuedEvent(inv.getId(), inv.getOrganizationId())); // -> Kafka after commit
    return mapper.toSummary(inv, nameOf(me));
  }

  // -------------------------------------------------------------------- read
  @Transactional(readOnly = true)
  public PageResponse<InvoiceSummary> list(InvoiceFilter filter, AuthPrincipal me, int page, int size, String sort) {
    Page<InvoiceEntity> result = invoices.findAll(filter.toSpec(me.organizationId(), scopeOf(me)),
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), parseSort(sort)));
    return PageResponse.of(result, mapper.toSummaries(result.getContent()));
  }

  /** All matching invoices for export, newest first, capped to keep memory bounded. */
  @Transactional(readOnly = true)
  public List<InvoiceEntity> listForExport(InvoiceFilter filter, AuthPrincipal me, int max) {
    return invoices.findAll(filter.toSpec(me.organizationId(), scopeOf(me)),
        PageRequest.of(0, max, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
  }

  @Transactional(readOnly = true)
  public InvoiceDetail get(UUID id, AuthPrincipal me) {
    return mapper.toDetail(load(id, me), me);
  }

  @Transactional(readOnly = true)
  public OcrTextResponse ocrText(UUID id, AuthPrincipal me) {
    InvoiceEntity i = load(id, me);
    return new OcrTextResponse(i.getOcrText(), i.getOcrEngine(), i.getOcrConfidence(), i.getOcrPageCount());
  }

  public record FileContent(byte[] data, String contentType, String filename) {}

  @Transactional(readOnly = true)
  public FileContent file(UUID id, AuthPrincipal me) {
    InvoiceEntity i = load(id, me);
    return new FileContent(storage.read(i.getStorageKey()), i.getContentType(), i.getOriginalFilename());
  }

  @Transactional(readOnly = true)
  public List<AuditEntryDto> trail(UUID id, AuthPrincipal me) {
    load(id, me); // authorisation check
    return audit.trailFor(me.organizationId(), AuditService.INVOICE, id);
  }

  // ----------------------------------------------------------------- correct
  @Transactional
  public InvoiceDetail update(UUID id, UpdateInvoiceRequest req, AuthPrincipal me) {
    InvoiceEntity inv = load(id, me);
    if (!inv.getStatus().isEditable()) {
      throw ApiException.conflict("invoice_not_editable", "Invoices in status " + inv.getStatus() + " cannot be edited");
    }
    if (req.version() != null && req.version() != inv.getVersion()) {
      throw ApiException.conflict("stale_version", "The invoice was changed by someone else; reload and try again");
    }

    Map<String, Object> changes = new LinkedHashMap<>();
    DocumentType type = req.documentType() == null ? inv.getDocumentType() : req.documentType();
    track(changes, "documentType", inv.getDocumentType(), type);
    inv.setDocumentType(type);
    String vendor = blankToNull(req.vendorName());
    track(changes, "vendorName", inv.getVendorName(), vendor);
    inv.setVendorName(vendor);
    inv.setVendorId(vendors.findOrCreate(inv.getOrganizationId(), vendor));
    String number = blankToNull(req.invoiceNumber());
    track(changes, "invoiceNumber", inv.getInvoiceNumber(), number);
    inv.setInvoiceNumber(number);
    track(changes, "invoiceDate", inv.getInvoiceDate(), req.invoiceDate());
    inv.setInvoiceDate(req.invoiceDate());
    track(changes, "dueDate", inv.getDueDate(), req.dueDate());
    inv.setDueDate(req.dueDate());
    String currency = blankToNull(req.currency()) == null ? null : req.currency().trim().toUpperCase(Locale.ROOT);
    track(changes, "currency", inv.getCurrency(), currency);
    inv.setCurrency(currency);
    // a credit note is stored negative whichever sign the reviewer typed (type.signed is the identity for invoices)
    track(changes, "subtotal", inv.getSubtotal(), type.signed(req.subtotal()));
    inv.setSubtotal(type.signed(req.subtotal()));
    track(changes, "tax", inv.getTax(), type.signed(req.tax()));
    inv.setTax(type.signed(req.tax()));
    track(changes, "total", inv.getTotal(), type.signed(req.total()));
    inv.setTotal(type.signed(req.total()));

    List<LineItemEntity> items = toLineItems(req.lineItems(), type);
    if (!sameLines(inv.getLineItems(), items)) changes.put("lineItems", items.size() + " line(s) edited");
    inv.replaceLineItems(items);

    InvoiceStatus before = inv.getStatus();
    inv.setHumanReviewed(true);
    InvoiceStatus next = validation.revalidate(inv);
    track(changes, "status", inv.getStatus(), next);
    inv.setStatus(next);
    invoices.saveAndFlush(inv);
    if (next != before) webhooks.invoiceEvent(next == InvoiceStatus.VALIDATED ? "invoice.validated" : "invoice.needs_review", inv);

    audit.log(me.organizationId(), me.userId(), "INVOICE_CORRECTED", AuditService.INVOICE, inv.getId(), changes);
    status.publish(inv);
    return mapper.toDetail(inv, me);
  }

  // ---------------------------------------------------------------- workflow
  @Transactional
  public InvoiceDetail approve(UUID id, DecisionRequest req, AuthPrincipal me) {
    InvoiceEntity inv = load(id, me);
    requireReviewer(me);
    if (inv.getStatus() != InvoiceStatus.VALIDATED) {
      throw ApiException.conflict("invoice_not_approvable",
          "Only validated invoices can be approved (current status: " + inv.getStatus() + ")");
    }
    if (me.role() == Role.APPROVER && inv.getUploadedBy().equals(me.userId())) {
      throw ApiException.forbidden("You cannot approve an invoice you uploaded yourself");
    }
    return decide(inv, InvoiceStatus.APPROVED, "INVOICE_APPROVED", req, me);
  }

  @Transactional
  public InvoiceDetail reject(UUID id, DecisionRequest req, AuthPrincipal me) {
    InvoiceEntity inv = load(id, me);
    requireReviewer(me);
    if (!InvoiceStatus.REJECTABLE.contains(inv.getStatus())) {
      throw ApiException.conflict("invoice_not_rejectable",
          "Invoices in status " + inv.getStatus() + " cannot be rejected");
    }
    if (req == null || req.comment() == null || req.comment().trim().length() < 3) {
      throw ApiException.badRequest("comment_required", "A comment explaining the rejection is required");
    }
    return decide(inv, InvoiceStatus.REJECTED, "INVOICE_REJECTED", req, me);
  }

  private InvoiceDetail decide(InvoiceEntity inv, InvoiceStatus target, String action, DecisionRequest req,
                               AuthPrincipal me) {
    String comment = req == null ? null : blankToNull(req.comment());
    InvoiceStatus from = inv.getStatus();
    inv.setStatus(target);
    inv.setDecidedBy(me.userId());
    inv.setDecidedAt(Instant.now(clock));
    inv.setDecisionComment(comment);
    invoices.saveAndFlush(inv);

    Map<String, Object> details = new LinkedHashMap<>();
    details.put("from", from.name());
    details.put("to", target.name());
    if (comment != null) details.put("comment", comment);
    audit.log(me.organizationId(), me.userId(), action, AuditService.INVOICE, inv.getId(), details);
    webhooks.invoiceEvent(target == InvoiceStatus.APPROVED ? "invoice.approved" : "invoice.rejected", inv);

    String label = inv.displayLabel();
    if (!inv.getUploadedBy().equals(me.userId())) {
      boolean approved = target == InvoiceStatus.APPROVED;
      notifications.notify(inv.getOrganizationId(), inv.getUploadedBy(), approved ? "APPROVED" : "REJECTED",
          approved ? "Invoice approved" : "Invoice rejected",
          label + (approved ? " was approved." : " was rejected" + (comment == null ? "." : ": " + comment)), inv.getId());
    }
    status.publish(inv);
    return mapper.toDetail(inv, me);
  }

  @Transactional
  public InvoiceDetail retry(UUID id, AuthPrincipal me) {
    InvoiceEntity inv = load(id, me);
    if (inv.getStatus() != InvoiceStatus.FAILED) {
      throw ApiException.conflict("invoice_not_retryable", "Only failed invoices can be retried");
    }
    inv.setStatus(InvoiceStatus.UPLOADED);
    inv.setFailureReason(null);
    inv.setProcessingFinishedAt(null);
    inv.setProcessingMs(null);
    invoices.saveAndFlush(inv);
    audit.log(me.organizationId(), me.userId(), "PROCESSING_RETRIED", AuditService.INVOICE, inv.getId(), null);
    status.publish(inv);
    events.publishEvent(new InvoiceQueuedEvent(inv.getId(), inv.getOrganizationId()));
    return mapper.toDetail(inv, me);
  }

  @Transactional
  public void delete(UUID id, AuthPrincipal me) {
    InvoiceEntity inv = load(id, me);
    if (me.role() != Role.ADMIN) throw ApiException.forbidden("Only admins can delete invoices");
    if (inv.getStatus() == InvoiceStatus.APPROVED) {
      throw ApiException.conflict("invoice_not_deletable", "Approved invoices are part of the books and cannot be deleted");
    }
    audit.log(me.organizationId(), me.userId(), "INVOICE_DELETED", AuditService.INVOICE, inv.getId(),
        Map.of("filename", inv.getOriginalFilename(), "status", inv.getStatus().name()));
    String key = inv.getStorageKey();
    invoices.delete(inv);
    invoices.flush();
    storage.delete(key);
  }

  // ----------------------------------------------------------------- helpers
  /** Tenant isolation + ownership in one place. Unknown and foreign ids are indistinguishable (404). */
  InvoiceEntity load(UUID id, AuthPrincipal me) {
    return invoices.findByIdAndOrganizationId(id, me.organizationId())
        .filter(i -> me.role().isReviewer() || i.getUploadedBy().equals(me.userId()))
        .orElseThrow(() -> ApiException.notFound("Invoice"));
  }

  /** USER sees only own uploads; reviewers see the whole organisation. */
  public static UUID scopeOf(AuthPrincipal me) {
    return me.role().isReviewer() ? null : me.userId();
  }

  private static void requireReviewer(AuthPrincipal me) {
    if (!me.role().isReviewer()) throw ApiException.forbidden("Only approvers and admins can decide on invoices");
  }

  private String nameOf(AuthPrincipal me) {
    return mapper.names(List.of(me.userId())).getOrDefault(me.userId(), me.email());
  }

  private static Sort parseSort(String sort) {
    String prop = "createdAt";
    Sort.Direction dir = Sort.Direction.DESC;
    if (sort != null && !sort.isBlank()) {
      String[] parts = sort.split(",");
      if (SORTABLE.contains(parts[0])) prop = parts[0];
      if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1])) dir = Sort.Direction.ASC;
    }
    Sort s = Sort.by(dir, prop);
    return prop.equals("createdAt") ? s : s.and(Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  private static List<LineItemEntity> toLineItems(List<LineItemDto> dtos, DocumentType type) {
    List<LineItemEntity> items = new ArrayList<>();
    if (dtos == null) return items;
    for (LineItemDto d : dtos) {
      LineItemEntity e = new LineItemEntity();
      e.setDescription(d.description().trim());
      e.setQuantity(type.signedQuantity(d.quantity()));
      e.setUnitPrice(type.signed(d.unitPrice()));
      e.setTotal(type.signed(d.total()));
      items.add(e);
    }
    return items;
  }

  private static boolean sameLines(List<LineItemEntity> old, List<LineItemEntity> next) {
    if (old.size() != next.size()) return false;
    for (int i = 0; i < old.size(); i++) {
      LineItemEntity a = old.get(i), b = next.get(i);
      if (!Objects.equals(a.getDescription(), b.getDescription()) || !eq(a.getQuantity(), b.getQuantity())
          || !eq(a.getUnitPrice(), b.getUnitPrice()) || !eq(a.getTotal(), b.getTotal())) return false;
    }
    return true;
  }

  private static void track(Map<String, Object> changes, String field, Object before, Object after) {
    boolean same = before instanceof BigDecimal a && after instanceof BigDecimal b ? a.compareTo(b) == 0
        : Objects.equals(before, after);
    if (!same) changes.put(field, display(before) + " -> " + display(after));
  }

  private static String display(Object v) {
    if (v == null) return "(empty)";
    if (v instanceof BigDecimal b) return b.setScale(2, RoundingMode.HALF_UP).toPlainString();
    return v.toString();
  }

  private static boolean eq(BigDecimal a, BigDecimal b) {
    return a == null ? b == null : b != null && a.compareTo(b) == 0;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
