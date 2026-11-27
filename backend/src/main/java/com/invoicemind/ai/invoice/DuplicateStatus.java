package com.invoicemind.ai.invoice;

/**
 * Result of the deterministic duplicate rules (see {@code InvoiceValidator}):
 * DUPLICATE = same vendor and invoice number as an earlier invoice, or an identical file (approval is blocked);
 * POSSIBLE_DUPLICATE = same vendor and total with a date close by but a different number (a person decides).
 */
public enum DuplicateStatus { NONE, DUPLICATE, POSSIBLE_DUPLICATE }
