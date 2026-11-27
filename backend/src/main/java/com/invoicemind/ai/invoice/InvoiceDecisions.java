package com.invoicemind.ai.invoice;

import com.invoicemind.ai.approval.ApprovalPolicyService;
import com.invoicemind.ai.approval.ApprovalPolicyService.Requirement;
import com.invoicemind.ai.approval.InvoiceApprovalEntity;
import com.invoicemind.ai.approval.InvoiceApprovalRepository;
import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.firm.UserOrgAccessRepository;
import com.invoicemind.ai.notification.NotificationService;
import com.invoicemind.ai.processing.StatusPublisher;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.vendor.VendorEntity;
import com.invoicemind.ai.vendor.VendorRepository;
import com.invoicemind.ai.webhook.WebhookOutbox;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisions on invoices: approvals under the approval rules (none, one or two approvers), rejection, and the
 * automatic approval of small invoices. Used by the invoice API, the processing pipeline and vendor confirmation.
 * Every decision is audited, notified and sent to webhooks.
 */
@Component
@RequiredArgsConstructor
public class InvoiceDecisions {
  private final InvoiceRepository invoices;
  private final InvoiceApprovalRepository approvals;
  private final ApprovalPolicyService policies;
  private final VendorRepository vendors;
  private final UserOrgAccessRepository access;
  private final AuditService audit;
  private final NotificationService notifications;
  private final WebhookOutbox webhooks;
  private final StatusPublisher status;
  private final Clock clock;

  public Requirement requirement(InvoiceEntity inv) {
    return policies.required(inv.getOrganizationId(), inv.getCurrency(), inv.getTotal());
  }

  public List<InvoiceApprovalEntity> approvalsOf(UUID invoiceId) {
    return approvals.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
  }

  public Set<UUID> approvers(UUID invoiceId) {
    return approvalsOf(invoiceId).stream().map(InvoiceApprovalEntity::getUserId).collect(Collectors.toSet());
  }

  public boolean vendorVerified(InvoiceEntity inv) {
    return inv.getVendorId() == null || vendors.findById(inv.getVendorId()).map(VendorEntity::isVerified).orElse(false);
  }

  /**
   * Records {@code me}'s approval. The invoice becomes APPROVED when it has as many approvals as its rule requires
   * (at least one); before that it stays VALIDATED and the other approvers are told a second approval is needed.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public InvoiceEntity approve(InvoiceEntity inv, String comment, AuthPrincipal me) {
    String blocked = ApprovalRules.blockedReason(inv, vendorVerified(inv), me, approvers(inv.getId()));
    if (blocked != null) {
      if (blocked.startsWith("You cannot approve")) throw ApiException.forbidden(blocked);
      throw ApiException.conflict("invoice_not_approvable", blocked);
    }
    Instant now = Instant.now(clock);
    approvals.saveAndFlush(new InvoiceApprovalEntity(inv.getId(), me.userId(), comment, now));
    int given = approvalsOf(inv.getId()).size();
    Requirement need = requirement(inv);
    int required = Math.max(need.approvals(), 1);
    if (given < required) {
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("approval", given + " of " + required);
      details.put("rule", need.reason());
      if (comment != null) details.put("comment", comment);
      audit.log(inv.getOrganizationId(), me.userId(), "APPROVAL_RECORDED", AuditService.INVOICE, inv.getId(), details);
      for (var person : access.findEnabledUsersWithRoleIn(inv.getOrganizationId(), Set.of(Role.APPROVER, Role.ADMIN))) {
        if (person.getId().equals(me.userId()) || person.getId().equals(inv.getUploadedBy())) continue;
        notifications.notify(inv.getOrganizationId(), person.getId(), "SECOND_APPROVAL", "Second approval needed",
            inv.displayLabel() + " has " + given + " of " + required + " approvals. " + need.reason() + ".", inv.getId());
      }
      status.publish(inv);
      return inv;
    }
    return decide(inv, InvoiceStatus.APPROVED, "INVOICE_APPROVED", comment, me.userId(), required > 1 ? need.reason() : null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public InvoiceEntity reject(InvoiceEntity inv, String comment, UUID userId) {
    return decide(inv, InvoiceStatus.REJECTED, "INVOICE_REJECTED", comment, userId, null);
  }

  /**
   * Small invoices whose rule says "automatic" are approved by the system as soon as they pass every check: status
   * VALIDATED (all deterministic checks green), the vendor confirmed by a person, no duplicate. Returns true if so.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean autoApproveIfEligible(InvoiceEntity inv) {
    if (inv.getStatus() != InvoiceStatus.VALIDATED || inv.getDuplicateStatus() != DuplicateStatus.NONE
        || !vendorVerified(inv)) {
      return false;
    }
    Requirement need = requirement(inv);
    if (need.approvals() != 0) return false;
    inv.setAutoApproved(true);
    decide(inv, InvoiceStatus.APPROVED, "INVOICE_AUTO_APPROVED", need.reason(), null, need.reason());
    return true;
  }

  /** Changes to the figures invalidate approvals already given: the approvers approved other numbers. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void resetApprovals(InvoiceEntity inv, UUID userId) {
    int removed = approvals.deleteByInvoiceId(inv.getId());
    if (removed > 0) {
      audit.log(inv.getOrganizationId(), userId, "APPROVALS_RESET", AuditService.INVOICE, inv.getId(),
          Map.of("removed", removed, "reason", "the invoice was corrected after it was approved"));
    }
  }

  private InvoiceEntity decide(InvoiceEntity inv, InvoiceStatus target, String action, String comment, UUID userId,
                               String rule) {
    InvoiceStatus from = inv.getStatus();
    inv.setStatus(target);
    inv.setDecidedBy(userId);
    inv.setDecidedAt(Instant.now(clock));
    inv.setDecisionComment(comment);
    invoices.saveAndFlush(inv);

    Map<String, Object> details = new LinkedHashMap<>();
    details.put("from", from.name());
    details.put("to", target.name());
    if (comment != null) details.put("comment", comment);
    if (rule != null) details.put("rule", rule);
    audit.log(inv.getOrganizationId(), userId, action, AuditService.INVOICE, inv.getId(), details);
    webhooks.invoiceEvent(target == InvoiceStatus.APPROVED ? "invoice.approved" : "invoice.rejected", inv);

    if (!inv.getUploadedBy().equals(userId)) {
      boolean approved = target == InvoiceStatus.APPROVED;
      String label = inv.displayLabel();
      notifications.notify(inv.getOrganizationId(), inv.getUploadedBy(), approved ? "APPROVED" : "REJECTED",
          approved ? "Invoice approved" : "Invoice rejected",
          label + (approved ? (userId == null ? " was approved automatically." : " was approved.")
              : " was rejected" + (comment == null ? "." : ": " + comment)), inv.getId());
    }
    status.publish(inv);
    return inv;
  }
}
