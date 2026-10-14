package com.invoicemind.ai.user;

public enum Role {
  USER, APPROVER, ADMIN;

  public String authority() { return "ROLE_" + name(); }

  /** APPROVER and ADMIN can see and act on the whole organisation. */
  public boolean isReviewer() { return this == APPROVER || this == ADMIN; }
}
