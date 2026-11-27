package com.invoicemind.ai.invoice;

import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.Role;

/**
 * The one place that decides whether an invoice may be approved by a given person, so the API check and the
 * UI hint can never disagree. Returns the reason approval is blocked, or null.
 */
public final class ApprovalRules {
  private ApprovalRules() {}

  public static String blockedReason(InvoiceEntity i, boolean vendorVerified, AuthPrincipal p) {
    if (!p.role().isReviewer()) return "Only approvers and admins can approve invoices";
    if (i.getDuplicateStatus() == DuplicateStatus.DUPLICATE) {
      return "This is a duplicate of an earlier invoice; it cannot be approved";
    }
    if (i.getDuplicateStatus() == DuplicateStatus.POSSIBLE_DUPLICATE) {
      return "Possible duplicate: decide whether it is a separate invoice first";
    }
    if (i.getStatus() != InvoiceStatus.VALIDATED) {
      return "Only validated invoices can be approved (current status: " + i.getStatus() + ")";
    }
    if (i.getVendorId() != null && !vendorVerified) return "Confirm the vendor before approving";
    if (p.role() == Role.APPROVER && i.getUploadedBy().equals(p.userId())) {
      return "You cannot approve an invoice you uploaded yourself";
    }
    return null;
  }
}
