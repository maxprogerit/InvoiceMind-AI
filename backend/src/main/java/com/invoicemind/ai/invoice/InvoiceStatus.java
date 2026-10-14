package com.invoicemind.ai.invoice;

import java.util.EnumSet;
import java.util.Set;

/**
 * UPLOADED -> PROCESSING -> EXTRACTED -> NEEDS_REVIEW | VALIDATED -> APPROVED | REJECTED, or FAILED at any
 * processing step. NEEDS_REVIEW returns to VALIDATED once a human correction passes validation.
 */
public enum InvoiceStatus {
  UPLOADED, PROCESSING, EXTRACTED, NEEDS_REVIEW, VALIDATED, APPROVED, REJECTED, FAILED;

  /** Fields can be corrected by a human. */
  public static final Set<InvoiceStatus> EDITABLE = EnumSet.of(NEEDS_REVIEW, VALIDATED);
  /** An approver may reject. Approval additionally requires VALIDATED. */
  public static final Set<InvoiceStatus> REJECTABLE = EnumSet.of(NEEDS_REVIEW, VALIDATED);
  /** Waiting for a human decision. */
  public static final Set<InvoiceStatus> AWAITING_DECISION = EnumSet.of(NEEDS_REVIEW, VALIDATED);
  /** Occupy the (vendor, number) identity for duplicate detection. */
  public static final Set<InvoiceStatus> COUNTS_FOR_DUPLICATES =
      EnumSet.of(EXTRACTED, NEEDS_REVIEW, VALIDATED, APPROVED);

  public boolean isEditable() { return EDITABLE.contains(this); }
}
