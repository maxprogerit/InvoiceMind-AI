package com.invoicemind.ai.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic business rules. The LLM output is untrusted input: nothing here depends on
 * what the model claims except the explicit "model flagged" signals, which can only add review work.
 */
public class InvoiceValidator {

  public enum Severity { ERROR, WARNING }

  public enum Code {
    MISSING_FIELD(Severity.ERROR, false),
    INVALID_CURRENCY(Severity.ERROR, false),
    NON_POSITIVE_TOTAL(Severity.ERROR, false),
    NEGATIVE_AMOUNT(Severity.ERROR, false),
    TOTAL_MISMATCH(Severity.ERROR, false),
    LINE_SUM_MISMATCH(Severity.ERROR, false),
    FUTURE_INVOICE_DATE(Severity.ERROR, false),
    INVOICE_DATE_TOO_OLD(Severity.ERROR, false),
    DUE_BEFORE_INVOICE(Severity.ERROR, false),
    DUPLICATE_INVOICE(Severity.ERROR, false),
    LINE_TOTAL_MISMATCH(Severity.WARNING, false),
    DUPLICATE_FILE(Severity.WARNING, false),
    LOW_CONFIDENCE(Severity.WARNING, true),
    MODEL_FLAGGED(Severity.WARNING, true),
    PROMPT_INJECTION_SUSPECTED(Severity.WARNING, true);

    private final Severity severity;
    private final boolean requiresHumanReview;

    Code(Severity severity, boolean requiresHumanReview) {
      this.severity = severity;
      this.requiresHumanReview = requiresHumanReview;
    }

    public Severity severity() { return severity; }
  }

  public record Line(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {}

  public record Input(
      UUID invoiceId, UUID organizationId, UUID vendorId, String vendorName, String invoiceNumber,
      LocalDate invoiceDate, LocalDate dueDate, String currency,
      BigDecimal subtotal, BigDecimal tax, BigDecimal total, List<Line> lines,
      String fileSha256, Map<String, Double> confidence,
      boolean modelNeedsReview, List<String> modelReasons, boolean injectionSuspected,
      boolean humanReviewed, Instant createdAt) {}

  public record Issue(Code code, String field, String message) {
    public Severity severity() { return code.severity; }
  }

  /** Database-backed lookups, injected so the rules stay unit-testable. */
  public interface Lookups {
    /** An older live invoice with the same vendor and number ("the older one wins" avoids flagging both). */
    Optional<UUID> duplicateOf(UUID organizationId, UUID invoiceId, UUID vendorId, String invoiceNumber,
                               Instant createdAt);

    boolean sameFileSeenBefore(UUID organizationId, UUID invoiceId, String sha256);
  }

  static final BigDecimal CENT = new BigDecimal("0.01");
  static final Set<String> KEY_CONFIDENCE_FIELDS = Set.of("vendor", "invoiceNumber", "invoiceDate", "total");
  static final double LOW_CONFIDENCE_THRESHOLD = 0.6;

  public List<Issue> validate(Input in, Lookups lookups, LocalDate today) {
    List<Issue> issues = new ArrayList<>();

    required(issues, "vendorName", in.vendorName());
    required(issues, "invoiceNumber", in.invoiceNumber());
    required(issues, "invoiceDate", in.invoiceDate());
    required(issues, "currency", in.currency());
    required(issues, "total", in.total());

    if (in.currency() != null && !in.currency().isBlank() && !isIsoCurrency(in.currency())) {
      issues.add(new Issue(Code.INVALID_CURRENCY, "currency", "'" + in.currency() + "' is not an ISO 4217 currency code"));
    }

    amounts(issues, in);
    dates(issues, in, today);
    lineItems(issues, in);
    duplicates(issues, in, lookups);

    if (!in.humanReviewed()) {
      lowConfidence(issues, in);
      if (in.modelNeedsReview()) {
        String reasons = in.modelReasons() == null || in.modelReasons().isEmpty()
            ? "no reason given" : String.join("; ", in.modelReasons());
        issues.add(new Issue(Code.MODEL_FLAGGED, null, "The extraction model asked for review: " + reasons));
      }
      if (in.injectionSuspected()) {
        issues.add(new Issue(Code.PROMPT_INJECTION_SUSPECTED, null,
            "The document contains text that looks like instructions to an AI system; verify every value"));
      }
    }
    return issues;
  }

  /** NEEDS_REVIEW if any error, or any warning that demands a human look; otherwise the invoice is VALIDATED. */
  public static boolean needsReview(List<Issue> issues) {
    return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR || i.code().requiresHumanReview);
  }

  // ---------------------------------------------------------------- rules
  private void required(List<Issue> issues, String field, Object value) {
    boolean missing = value == null || (value instanceof String s && s.isBlank());
    if (missing) issues.add(new Issue(Code.MISSING_FIELD, field, "Required field '" + field + "' is missing"));
  }

  private void amounts(List<Issue> issues, Input in) {
    if (in.total() != null && in.total().signum() <= 0) {
      issues.add(new Issue(Code.NON_POSITIVE_TOTAL, "total", "Total must be greater than zero"));
    }
    if (in.subtotal() != null && in.subtotal().signum() < 0) {
      issues.add(new Issue(Code.NEGATIVE_AMOUNT, "subtotal", "Subtotal must not be negative"));
    }
    if (in.tax() != null && in.tax().signum() < 0) {
      issues.add(new Issue(Code.NEGATIVE_AMOUNT, "tax", "Tax must not be negative"));
    }
    if (in.subtotal() != null && in.total() != null) {
      BigDecimal expected = in.subtotal().add(nz(in.tax()));
      if (differs(expected, in.total(), CENT)) {
        issues.add(new Issue(Code.TOTAL_MISMATCH, "total", "Subtotal " + fmt(in.subtotal()) + " + tax "
            + fmt(nz(in.tax())) + " = " + fmt(expected) + ", but the total is " + fmt(in.total())));
      }
    }
  }

  private void dates(List<Issue> issues, Input in, LocalDate today) {
    LocalDate date = in.invoiceDate();
    if (date != null) {
      if (date.isAfter(today.plusDays(1))) {
        issues.add(new Issue(Code.FUTURE_INVOICE_DATE, "invoiceDate", "Invoice date " + date + " is in the future"));
      }
      if (date.isBefore(today.minusYears(10))) {
        issues.add(new Issue(Code.INVOICE_DATE_TOO_OLD, "invoiceDate",
            "Invoice date " + date + " is more than 10 years ago, likely a misread"));
      }
    }
    if (date != null && in.dueDate() != null && in.dueDate().isBefore(date)) {
      issues.add(new Issue(Code.DUE_BEFORE_INVOICE, "dueDate",
          "Due date " + in.dueDate() + " is before the invoice date " + date));
    }
  }

  private void lineItems(List<Issue> issues, Input in) {
    List<Line> lines = in.lines() == null ? List.of() : in.lines();
    if (lines.isEmpty()) return;

    for (int i = 0; i < lines.size(); i++) {
      Line l = lines.get(i);
      if (l.quantity() != null && l.unitPrice() != null && l.total() != null) {
        BigDecimal expected = l.quantity().multiply(l.unitPrice()).setScale(2, RoundingMode.HALF_UP);
        if (differs(expected, l.total(), CENT)) {
          issues.add(new Issue(Code.LINE_TOTAL_MISMATCH, "lineItems[" + i + "]", "Line " + (i + 1) + ": "
              + fmt(l.quantity()) + " x " + fmt(l.unitPrice()) + " = " + fmt(expected) + ", but the line total is "
              + fmt(l.total())));
        }
      }
    }
    if (lines.stream().anyMatch(l -> l.total() == null)) return; // cannot sum an incomplete table

    BigDecimal sum = lines.stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal tolerance = CENT.multiply(BigDecimal.valueOf(Math.max(1, lines.size()))); // one cent of rounding per line
    if (in.subtotal() != null) {
      if (differs(sum, in.subtotal(), tolerance)) {
        issues.add(new Issue(Code.LINE_SUM_MISMATCH, "subtotal",
            "Line items add up to " + fmt(sum) + ", but the subtotal is " + fmt(in.subtotal())));
      }
    } else if (in.total() != null && differs(sum.add(nz(in.tax())), in.total(), tolerance)) {
      issues.add(new Issue(Code.LINE_SUM_MISMATCH, "total",
          "Line items (" + fmt(sum) + ") plus tax (" + fmt(nz(in.tax())) + ") do not equal the total " + fmt(in.total())));
    }
  }

  private void duplicates(List<Issue> issues, Input in, Lookups lookups) {
    if (in.vendorId() != null && in.invoiceNumber() != null && !in.invoiceNumber().isBlank()) {
      lookups.duplicateOf(in.organizationId(), in.invoiceId(), in.vendorId(), in.invoiceNumber().trim(), in.createdAt())
          .ifPresent(other -> issues.add(new Issue(Code.DUPLICATE_INVOICE, "invoiceNumber",
              "Invoice " + in.invoiceNumber() + " from " + in.vendorName() + " already exists (id " + other + ")")));
    }
    if (in.fileSha256() != null && lookups.sameFileSeenBefore(in.organizationId(), in.invoiceId(), in.fileSha256())) {
      issues.add(new Issue(Code.DUPLICATE_FILE, null, "An identical file was uploaded before"));
    }
  }

  private void lowConfidence(List<Issue> issues, Input in) {
    if (in.confidence() == null) return;
    in.confidence().forEach((field, value) -> {
      if (KEY_CONFIDENCE_FIELDS.contains(field) && value != null && value < LOW_CONFIDENCE_THRESHOLD) {
        issues.add(new Issue(Code.LOW_CONFIDENCE, field,
            "Low extraction confidence for '" + field + "' (" + Math.round(value * 100) + "%)"));
      }
    });
  }

  // -------------------------------------------------------------- helpers
  static boolean isIsoCurrency(String code) {
    if (code == null || !code.matches("[A-Za-z]{3}")) return false;
    try {
      Currency.getInstance(code.toUpperCase());
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static boolean differs(BigDecimal a, BigDecimal b, BigDecimal tolerance) {
    return a.subtract(b).abs().compareTo(tolerance) > 0;
  }

  private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

  private static String fmt(BigDecimal v) { return v.stripTrailingZeros().scale() < 2 ? v.setScale(2, RoundingMode.HALF_UP).toPlainString() : v.toPlainString(); }
}
