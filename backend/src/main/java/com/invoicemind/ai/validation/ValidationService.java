package com.invoicemind.ai.validation;

import com.invoicemind.ai.invoice.DocumentType;
import com.invoicemind.ai.invoice.DuplicateStatus;
import com.invoicemind.ai.invoice.InvoiceEntity;
import com.invoicemind.ai.invoice.InvoiceRepository;
import com.invoicemind.ai.invoice.InvoiceStatus;
import com.invoicemind.ai.invoice.LineItemEntity;
import com.invoicemind.ai.invoice.ValidationIssueEntity;
import com.invoicemind.ai.validation.InvoiceValidator.Candidate;
import com.invoicemind.ai.validation.InvoiceValidator.Code;
import com.invoicemind.ai.validation.InvoiceValidator.Issue;
import com.invoicemind.ai.vendor.VendorEntity;
import com.invoicemind.ai.vendor.VendorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Runs {@link InvoiceValidator} against an invoice entity and stores the resulting issues on it. */
@Service
public class ValidationService {
  private final InvoiceRepository invoices;
  private final VendorRepository vendors;
  private final Clock clock;
  private final InvoiceValidator validator;

  public ValidationService(InvoiceRepository invoices, VendorRepository vendors, Clock clock,
                           @Value("${app.duplicates.window-days:7}") int possibleDuplicateWindowDays) {
    this.invoices = invoices;
    this.vendors = vendors;
    this.clock = clock;
    this.validator = new InvoiceValidator(possibleDuplicateWindowDays);
  }

  /**
   * Replaces the invoice's validation issues, sets its duplicate status from the duplicate findings, and returns
   * the status it should have afterwards (NEEDS_REVIEW or VALIDATED). The caller persists the status.
   */
  public InvoiceStatus revalidate(InvoiceEntity invoice) {
    List<Issue> issues = validator.validate(toInput(invoice), lookups(), LocalDate.now(clock));
    invoice.replaceIssues(issues.stream().map(ValidationService::toEntity).toList());
    applyDuplicateStatus(invoice, issues);
    return InvoiceValidator.needsReview(issues) ? InvoiceStatus.NEEDS_REVIEW : InvoiceStatus.VALIDATED;
  }

  /**
   * The duplicate rules only look back in time ("the older one wins"), so when an invoice changes (it was just
   * extracted, or a person corrected it) a newer open invoice of the same vendor may now match it. Those are
   * re-checked here; returns the ones whose status or duplicate status changed, for the caller to publish.
   */
  public List<InvoiceEntity> recheckNewerOfSameVendor(InvoiceEntity changed) {
    if (changed.getVendorId() == null || changed.getCreatedAt() == null) return List.of();
    List<InvoiceEntity> touched = new java.util.ArrayList<>();
    for (InvoiceEntity newer : invoices.findByOrganizationIdAndVendorIdAndStatusInAndCreatedAtAfterOrderByCreatedAtAsc(
        changed.getOrganizationId(), changed.getVendorId(), InvoiceStatus.AWAITING_DECISION, changed.getCreatedAt())) {
      InvoiceStatus before = newer.getStatus();
      DuplicateStatus duplicateBefore = newer.getDuplicateStatus();
      newer.setStatus(revalidate(newer));
      invoices.saveAndFlush(newer);
      if (newer.getStatus() != before || newer.getDuplicateStatus() != duplicateBefore) touched.add(newer);
    }
    return touched;
  }

  static void applyDuplicateStatus(InvoiceEntity invoice, List<Issue> issues) {
    Optional<Issue> exact = issues.stream()
        .filter(i -> i.code() == Code.DUPLICATE_INVOICE || i.code() == Code.DUPLICATE_FILE).findFirst();
    Optional<Issue> possible = issues.stream().filter(i -> i.code() == Code.POSSIBLE_DUPLICATE).findFirst();
    if (exact.isPresent()) {
      invoice.setDuplicateStatus(DuplicateStatus.DUPLICATE);
      invoice.setDuplicateOf(exact.get().related());
    } else if (possible.isPresent()) {
      invoice.setDuplicateStatus(DuplicateStatus.POSSIBLE_DUPLICATE);
      invoice.setDuplicateOf(possible.get().related());
    } else {
      invoice.setDuplicateStatus(DuplicateStatus.NONE);
      invoice.setDuplicateOf(null);
    }
  }

  private InvoiceValidator.Input toInput(InvoiceEntity i) {
    List<InvoiceValidator.Line> lines = i.getLineItems().stream().map(this::toLine).toList();
    return new InvoiceValidator.Input(i.getId(), i.getOrganizationId(), i.getDocumentType(), i.getVendorId(),
        i.getVendorName(),
        i.getInvoiceNumber(), i.getInvoiceDate(), i.getDueDate(), i.getCurrency(), i.getSubtotal(), i.getTax(),
        i.getTotal(), lines, i.getFileSha256(), i.getFieldConfidence(), i.isModelNeedsReview(),
        i.getModelReviewReasons(), i.isInjectionSuspected(), i.isHumanReviewed(), i.getCreatedAt(),
        i.getVendorId() == null || vendors.findById(i.getVendorId()).map(VendorEntity::isVerified).orElse(false),
        i.getDuplicateClearedAt() != null);
  }

  private InvoiceValidator.Line toLine(LineItemEntity l) {
    return new InvoiceValidator.Line(l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.getTotal());
  }

  private InvoiceValidator.Lookups lookups() {
    return new InvoiceValidator.Lookups() {
      @Override
      public Optional<UUID> duplicateOf(UUID org, UUID id, DocumentType type, UUID vendorId, String number,
                                        Instant createdAt) {
        return invoices.findDuplicateIds(org, id, type, vendorId, number, InvoiceStatus.COUNTS_FOR_DUPLICATES, createdAt)
            .stream().findFirst();
      }

      @Override
      public Optional<UUID> earlierSameFile(UUID org, UUID id, String sha256, Instant createdAt) {
        return invoices.findEarlierSameFile(org, id, sha256, InvoiceStatus.COUNTS_FOR_DUPLICATES, createdAt).stream()
            .findFirst().map(InvoiceEntity::getId);
      }

      @Override
      public Optional<Candidate> possibleDuplicateOf(UUID org, UUID id, DocumentType type, UUID vendorId,
                                                     BigDecimal total, String currency, LocalDate date, int windowDays,
                                                     Instant createdAt) {
        return invoices.findPossibleDuplicates(org, id, type, vendorId, total, currency, date.minusDays(windowDays),
                date.plusDays(windowDays), InvoiceStatus.COUNTS_FOR_DUPLICATES, createdAt).stream()
            .findFirst().map(c -> new Candidate(c.getId(), c.getInvoiceNumber(), c.getInvoiceDate()));
      }
    };
  }

  private static ValidationIssueEntity toEntity(Issue issue) {
    ValidationIssueEntity e = new ValidationIssueEntity();
    e.setCode(issue.code().name());
    e.setSeverity(issue.severity().name());
    e.setField(issue.field());
    e.setMessage(issue.message().length() > 500 ? issue.message().substring(0, 497) + "..." : issue.message());
    e.setRelatedInvoiceId(issue.related());
    return e;
  }
}
