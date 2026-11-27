package com.invoicemind.ai.invoice;

import java.util.EnumSet;
import java.util.Set;

/** The work queues of the review screen. Each one is a plain filter on the invoice list. */
public enum ReviewQueue {
  /** A check failed: a person has to look. */
  NEEDS_REVIEW,
  /** Same vendor and total close by: a person decides whether it is the same bill. */
  POSSIBLE_DUPLICATE,
  /** The vendor was created from a document and nobody confirmed it yet. */
  UNVERIFIED_VENDOR,
  /** The due date has passed and the invoice is still open (not rejected, not yet exported to accounting). */
  OVERDUE;

  /** Invoices that still need something from someone. */
  public static final Set<InvoiceStatus> OPEN = EnumSet.of(InvoiceStatus.EXTRACTED, InvoiceStatus.NEEDS_REVIEW,
      InvoiceStatus.VALIDATED);
  /** Invoices that can still be paid late: open ones and approved ones. */
  public static final Set<InvoiceStatus> PAYABLE = EnumSet.of(InvoiceStatus.EXTRACTED, InvoiceStatus.NEEDS_REVIEW,
      InvoiceStatus.VALIDATED, InvoiceStatus.APPROVED);
}
