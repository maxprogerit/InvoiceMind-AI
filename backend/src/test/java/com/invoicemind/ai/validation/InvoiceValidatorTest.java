package com.invoicemind.ai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.invoicemind.ai.invoice.DocumentType;
import com.invoicemind.ai.validation.InvoiceValidator.Code;
import com.invoicemind.ai.validation.InvoiceValidator.Input;
import com.invoicemind.ai.validation.InvoiceValidator.Issue;
import com.invoicemind.ai.validation.InvoiceValidator.Line;
import com.invoicemind.ai.validation.InvoiceValidator.Lookups;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceValidatorTest {
  static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
  static final UUID ORG = UUID.randomUUID();
  static final UUID VENDOR = UUID.randomUUID();

  final InvoiceValidator validator = new InvoiceValidator();
  final Lookups noDuplicates = new Lookups() {
    public Optional<UUID> duplicateOf(UUID o, UUID i, DocumentType t, UUID v, String n, Instant c) { return Optional.empty(); }
    public boolean sameFileSeenBefore(UUID o, UUID i, String s) { return false; }
  };

  static BigDecimal d(String v) { return new BigDecimal(v); }

  List<Issue> run(Input in) { return validator.validate(in, noDuplicates, TODAY); }

  /** A fully valid baseline invoice; each test mutates one thing through {@code change}. */
  Input base(java.util.function.UnaryOperator<Fields> change) {
    Fields f = change.apply(new Fields());
    return new Input(UUID.randomUUID(), ORG, f.type, VENDOR, f.vendorName, f.invoiceNumber, f.invoiceDate, f.dueDate,
        f.currency, f.subtotal, f.tax, f.total, f.lines, "sha", f.confidence, f.modelNeedsReview, f.modelReasons,
        f.injection, f.humanReviewed, Instant.now());
  }

  static class Fields {
    DocumentType type = DocumentType.INVOICE;
    String vendorName = "ACME GmbH";
    String invoiceNumber = "INV-1";
    LocalDate invoiceDate = LocalDate.of(2026, 6, 1);
    LocalDate dueDate = LocalDate.of(2026, 7, 1);
    String currency = "EUR";
    BigDecimal subtotal = d("1000.00");
    BigDecimal tax = d("190.00");
    BigDecimal total = d("1190.00");
    List<Line> lines = List.of(new Line("Consulting", d("10"), d("100.00"), d("1000.00")));
    Map<String, Double> confidence = Map.of("vendor", 0.98, "invoiceNumber", 0.99, "invoiceDate", 0.95, "total", 0.99);
    boolean modelNeedsReview = false;
    List<String> modelReasons = List.of();
    boolean injection = false;
    boolean humanReviewed = false;
  }

  List<Code> codes(List<Issue> issues) { return issues.stream().map(Issue::code).toList(); }

  @Test
  void validInvoiceHasNoIssues() {
    List<Issue> issues = run(base(f -> f));
    assertThat(issues).isEmpty();
    assertThat(InvoiceValidator.needsReview(issues)).isFalse();
  }

  @Test
  void missingRequiredFieldsAreErrors() {
    List<Issue> issues = run(base(f -> {
      f.vendorName = " ";
      f.invoiceNumber = null;
      f.invoiceDate = null;
      f.currency = null;
      f.total = null;
      f.subtotal = null;
      f.lines = List.of();
      return f;
    }));
    assertThat(issues).filteredOn(i -> i.code() == Code.MISSING_FIELD).extracting(Issue::field)
        .containsExactlyInAnyOrder("vendorName", "invoiceNumber", "invoiceDate", "currency", "total");
    assertThat(InvoiceValidator.needsReview(issues)).isTrue();
  }

  @Test
  void invalidCurrencyIsRejected() {
    assertThat(codes(run(base(f -> { f.currency = "EURO"; return f; })))).contains(Code.INVALID_CURRENCY);
    assertThat(codes(run(base(f -> { f.currency = "XYZ"; return f; })))).contains(Code.INVALID_CURRENCY);
    assertThat(codes(run(base(f -> { f.currency = "usd"; return f; })))).doesNotContain(Code.INVALID_CURRENCY);
  }

  @Test
  void subtotalPlusTaxMustEqualTotal() {
    assertThat(codes(run(base(f -> { f.total = d("1200.00"); return f; })))).contains(Code.TOTAL_MISMATCH);
    // one cent of rounding is tolerated, two are not
    assertThat(codes(run(base(f -> { f.total = d("1190.01"); return f; })))).doesNotContain(Code.TOTAL_MISMATCH);
    assertThat(codes(run(base(f -> { f.total = d("1190.02"); return f; })))).contains(Code.TOTAL_MISMATCH);
  }

  @Test
  void missingTaxIsTreatedAsZero() {
    List<Issue> ok = run(base(f -> { f.tax = null; f.total = d("1000.00"); return f; }));
    assertThat(codes(ok)).doesNotContain(Code.TOTAL_MISMATCH);
  }

  @Test
  void lineItemsMustAddUpToSubtotal() {
    List<Issue> issues = run(base(f -> {
      f.lines = List.of(new Line("A", d("1"), d("400.00"), d("400.00")), new Line("B", d("1"), d("500.00"), d("500.00")));
      return f;
    }));
    assertThat(codes(issues)).contains(Code.LINE_SUM_MISMATCH);
    Issue issue = issues.stream().filter(i -> i.code() == Code.LINE_SUM_MISMATCH).findFirst().orElseThrow();
    assertThat(issue.message()).contains("900.00").contains("1000.00");
  }

  @Test
  void lineSumFallsBackToTotalWhenSubtotalMissing() {
    List<Issue> issues = run(base(f -> { f.subtotal = null; f.total = d("1500.00"); return f; }));
    assertThat(codes(issues)).contains(Code.LINE_SUM_MISMATCH);
  }

  @Test
  void lineArithmeticMismatchIsOnlyAWarning() {
    List<Issue> issues = run(base(f -> {
      f.lines = List.of(new Line("Consulting", d("10"), d("100.00"), d("999.00")));
      f.subtotal = d("999.00");
      f.total = d("1189.00");
      return f;
    }));
    assertThat(issues).filteredOn(i -> i.code() == Code.LINE_TOTAL_MISMATCH)
        .singleElement().satisfies(i -> assertThat(i.severity()).isEqualTo(InvoiceValidator.Severity.WARNING));
    assertThat(InvoiceValidator.needsReview(issues)).isFalse();
  }

  @Test
  void dateSanity() {
    assertThat(codes(run(base(f -> { f.invoiceDate = TODAY.plusDays(5); f.dueDate = null; return f; }))))
        .contains(Code.FUTURE_INVOICE_DATE);
    assertThat(codes(run(base(f -> { f.invoiceDate = TODAY.plusDays(1); f.dueDate = null; return f; }))))
        .doesNotContain(Code.FUTURE_INVOICE_DATE); // timezone slack
    assertThat(codes(run(base(f -> { f.invoiceDate = TODAY.minusYears(11); f.dueDate = null; return f; }))))
        .contains(Code.INVOICE_DATE_TOO_OLD);
    assertThat(codes(run(base(f -> { f.dueDate = f.invoiceDate.minusDays(1); return f; }))))
        .contains(Code.DUE_BEFORE_INVOICE);
  }

  @Test
  void amountSanity() {
    assertThat(codes(run(base(f -> { f.total = d("0.00"); f.subtotal = d("0.00"); f.tax = d("0.00"); return f; }))))
        .contains(Code.NON_POSITIVE_TOTAL);
    assertThat(codes(run(base(f -> { f.tax = d("-10.00"); f.total = d("990.00"); return f; }))))
        .contains(Code.NEGATIVE_AMOUNT);
  }

  /** A valid credit note: stored negative, arithmetic holds with signs. */
  Input creditNote(java.util.function.UnaryOperator<Fields> change) {
    return base(f -> {
      f.type = DocumentType.CREDIT_NOTE;
      f.subtotal = d("-200.00");
      f.tax = d("-38.00");
      f.total = d("-238.00");
      f.lines = List.of(new Line("Returned hours", d("2"), d("-100.00"), d("-200.00")));
      return change.apply(f);
    });
  }

  @Test
  void validCreditNoteHasNoIssues() {
    assertThat(run(creditNote(f -> f))).isEmpty();
  }

  @Test
  void creditNoteArithmeticIsStillChecked() {
    assertThat(codes(run(creditNote(f -> { f.total = d("-250.00"); return f; })))).contains(Code.TOTAL_MISMATCH);
    assertThat(codes(run(creditNote(f -> { f.subtotal = d("-150.00"); f.total = d("-188.00"); return f; }))))
        .contains(Code.LINE_SUM_MISMATCH);
  }

  @Test
  void creditNoteSignRulesAreTheMirrorOfInvoiceRules() {
    assertThat(codes(run(creditNote(f -> {
      f.subtotal = d("0.00"); f.tax = d("0.00"); f.total = d("0.00"); f.lines = List.of(); return f;
    })))).contains(Code.NON_NEGATIVE_CREDIT_TOTAL);
    assertThat(codes(run(creditNote(f -> { f.tax = d("38.00"); f.total = d("-162.00"); return f; }))))
        .contains(Code.POSITIVE_CREDIT_AMOUNT);
    // and a negative total on a plain invoice is still an error
    assertThat(codes(run(base(f -> { f.subtotal = d("-1000.00"); f.tax = d("-190.00"); f.total = d("-1190.00"); return f; }))))
        .contains(Code.NON_POSITIVE_TOTAL, Code.NEGATIVE_AMOUNT);
  }

  @Test
  void documentTypeSignHelpersNormaliseWhateverWasPrinted() {
    assertThat(DocumentType.CREDIT_NOTE.signed(d("523.60"))).isEqualByComparingTo("-523.60");
    assertThat(DocumentType.CREDIT_NOTE.signed(d("-523.60"))).isEqualByComparingTo("-523.60");
    assertThat(DocumentType.CREDIT_NOTE.signed(null)).isNull();
    assertThat(DocumentType.INVOICE.signed(d("-5"))).isEqualByComparingTo("-5"); // invoices are never touched
    assertThat(DocumentType.CREDIT_NOTE.signedQuantity(d("-2"))).isEqualByComparingTo("2");
    assertThat(DocumentType.parse("credit_note")).isEqualTo(DocumentType.CREDIT_NOTE);
    assertThat(DocumentType.parse("Credit-Note")).isEqualTo(DocumentType.CREDIT_NOTE);
    assertThat(DocumentType.parse(null)).isEqualTo(DocumentType.INVOICE);
    assertThat(DocumentType.parse("something else")).isEqualTo(DocumentType.INVOICE);
  }

  @Test
  void duplicateInvoiceIsAnError() {
    UUID other = UUID.randomUUID();
    Lookups dup = new Lookups() {
      public Optional<UUID> duplicateOf(UUID o, UUID i, DocumentType t, UUID v, String n, Instant c) { return Optional.of(other); }
      public boolean sameFileSeenBefore(UUID o, UUID i, String s) { return true; }
    };
    List<Issue> issues = validator.validate(base(f -> f), dup, TODAY);
    assertThat(codes(issues)).contains(Code.DUPLICATE_INVOICE, Code.DUPLICATE_FILE);
    assertThat(issues.stream().filter(i -> i.code() == Code.DUPLICATE_INVOICE).findFirst().orElseThrow().message())
        .contains(other.toString());
  }

  @Test
  void duplicateLookupIsSkippedWithoutVendorOrNumber() {
    Lookups mustNotBeCalled = new Lookups() {
      public Optional<UUID> duplicateOf(UUID o, UUID i, DocumentType t, UUID v, String n, Instant c) { throw new AssertionError(); }
      public boolean sameFileSeenBefore(UUID o, UUID i, String s) { return false; }
    };
    Input in = new Input(UUID.randomUUID(), ORG, DocumentType.INVOICE, null, "ACME", "INV-1", TODAY, null, "EUR", d("1.00"), null, d("1.00"),
        List.of(), "sha", Map.of(), false, List.of(), false, false, Instant.now());
    validator.validate(in, mustNotBeCalled, TODAY); // no exception == pass
  }

  @Test
  void modelSignalsForceReviewUntilAHumanHasReviewed() {
    List<Issue> flagged = run(base(f -> {
      f.modelNeedsReview = true;
      f.modelReasons = List.of("ambiguous date");
      f.injection = true;
      f.confidence = Map.of("vendor", 0.4, "total", 0.99);
      return f;
    }));
    assertThat(codes(flagged)).contains(Code.MODEL_FLAGGED, Code.PROMPT_INJECTION_SUSPECTED, Code.LOW_CONFIDENCE);
    assertThat(InvoiceValidator.needsReview(flagged)).isTrue();

    List<Issue> afterHuman = run(base(f -> {
      f.modelNeedsReview = true;
      f.injection = true;
      f.confidence = Map.of("vendor", 0.4);
      f.humanReviewed = true;
      return f;
    }));
    assertThat(afterHuman).isEmpty();
    assertThat(InvoiceValidator.needsReview(afterHuman)).isFalse();
  }

  @Test
  void humanReviewDoesNotSilenceArithmeticErrors() {
    List<Issue> issues = run(base(f -> { f.humanReviewed = true; f.total = d("5.00"); return f; }));
    assertThat(codes(issues)).contains(Code.TOTAL_MISMATCH);
    assertThat(InvoiceValidator.needsReview(issues)).isTrue();
  }
}
