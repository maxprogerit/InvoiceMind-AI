package com.invoicemind.ai.user;

import java.util.Locale;

/**
 * Roles inside one company. Everyone sees the whole organisation's invoices.
 * <ul>
 *   <li>ACCOUNTANT: uploads, reviews and corrects invoices, confirms vendors; cannot approve.</li>
 *   <li>APPROVER: approves and rejects (never their own uploads), within the approval rules.</li>
 *   <li>ADMIN: everything, plus team, settings and approval rules; also never approves their own uploads.</li>
 * </ul>
 */
public enum Role {
  ACCOUNTANT, APPROVER, ADMIN;

  public String authority() { return "ROLE_" + name(); }

  /** APPROVER and ADMIN decide on invoices. */
  public boolean isReviewer() { return this == APPROVER || this == ADMIN; }

  /** Accepts the pre-V13 name USER (tokens and settings written before the rename). */
  public static Role parse(String value) {
    String v = value.trim().toUpperCase(Locale.ROOT);
    return "USER".equals(v) ? ACCOUNTANT : valueOf(v);
  }
}
