package com.invoicemind.ai.validation;

import com.invoicemind.ai.invoice.InvoiceEntity;
import com.invoicemind.ai.invoice.InvoiceRepository;
import com.invoicemind.ai.invoice.InvoiceStatus;
import com.invoicemind.ai.invoice.LineItemEntity;
import com.invoicemind.ai.invoice.ValidationIssueEntity;
import com.invoicemind.ai.validation.InvoiceValidator.Issue;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Runs {@link InvoiceValidator} against an invoice entity and stores the resulting issues on it. */
@Service
@RequiredArgsConstructor
public class ValidationService {
  private final InvoiceRepository invoices;
  private final Clock clock;
  private final InvoiceValidator validator = new InvoiceValidator();

  /**
   * Replaces the invoice's validation issues and returns the status it should have afterwards
   * (NEEDS_REVIEW or VALIDATED). The caller persists the status.
   */
  public InvoiceStatus revalidate(InvoiceEntity invoice) {
    List<Issue> issues = validator.validate(toInput(invoice), lookups(), LocalDate.now(clock));
    invoice.replaceIssues(issues.stream().map(ValidationService::toEntity).toList());
    return InvoiceValidator.needsReview(issues) ? InvoiceStatus.NEEDS_REVIEW : InvoiceStatus.VALIDATED;
  }

  private InvoiceValidator.Input toInput(InvoiceEntity i) {
    List<InvoiceValidator.Line> lines = i.getLineItems().stream().map(this::toLine).toList();
    return new InvoiceValidator.Input(i.getId(), i.getOrganizationId(), i.getVendorId(), i.getVendorName(),
        i.getInvoiceNumber(), i.getInvoiceDate(), i.getDueDate(), i.getCurrency(), i.getSubtotal(), i.getTax(),
        i.getTotal(), lines, i.getFileSha256(), i.getFieldConfidence(), i.isModelNeedsReview(),
        i.getModelReviewReasons(), i.isInjectionSuspected(), i.isHumanReviewed(), i.getCreatedAt());
  }

  private InvoiceValidator.Line toLine(LineItemEntity l) {
    return new InvoiceValidator.Line(l.getDescription(), l.getQuantity(), l.getUnitPrice(), l.getTotal());
  }

  private InvoiceValidator.Lookups lookups() {
    return new InvoiceValidator.Lookups() {
      @Override
      public Optional<UUID> duplicateOf(UUID org, UUID id, UUID vendorId, String number, Instant createdAt) {
        return invoices.findDuplicateIds(org, id, vendorId, number, InvoiceStatus.COUNTS_FOR_DUPLICATES, createdAt)
            .stream().findFirst();
      }

      @Override
      public boolean sameFileSeenBefore(UUID org, UUID id, String sha256) {
        return invoices.existsByOrganizationIdAndFileSha256AndIdNotAndStatusNot(org, sha256, id, InvoiceStatus.FAILED);
      }
    };
  }

  private static ValidationIssueEntity toEntity(Issue issue) {
    ValidationIssueEntity e = new ValidationIssueEntity();
    e.setCode(issue.code().name());
    e.setSeverity(issue.severity().name());
    e.setField(issue.field());
    e.setMessage(issue.message().length() > 500 ? issue.message().substring(0, 497) + "..." : issue.message());
    return e;
  }
}
