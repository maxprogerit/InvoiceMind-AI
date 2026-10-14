package com.invoicemind.ai.invoice;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * What kind of document an upload is. Credit notes reduce what is owed, so they are stored with
 * NEGATIVE amounts: sums in analytics and exports then net out without any special casing.
 */
public enum DocumentType {
  INVOICE, CREDIT_NOTE;

  /** Tolerant parse of the ai-service value ("credit_note"); anything unknown or missing is an invoice. */
  public static DocumentType parse(String raw) {
    if (raw == null) return INVOICE;
    return switch (raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_')) {
      case "credit_note", "creditnote" -> CREDIT_NOTE;
      default -> INVOICE;
    };
  }

  public boolean isCredit() { return this == CREDIT_NOTE; }

  /** Header amounts: a credit note is always stored as a non-positive value, whichever sign was printed. */
  public BigDecimal signed(BigDecimal amount) {
    if (amount == null || this == INVOICE) return amount;
    return amount.signum() == 0 ? amount : amount.abs().negate();
  }

  /** Unit price and line total follow the document sign; the quantity stays positive. */
  public BigDecimal signedQuantity(BigDecimal quantity) {
    return quantity == null || this == INVOICE ? quantity : quantity.abs();
  }

  public String label() { return this == CREDIT_NOTE ? "Credit note" : "Invoice"; }
}
