package com.invoicemind.ai.approval;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Approval rules per currency, and how many approvals an invoice needs under them. */
@Service
@RequiredArgsConstructor
public class ApprovalPolicyService {
  public record Rule(@NotNull @Pattern(regexp = "[A-Za-z]{3}") String currency,
                     @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal autoApproveBelow,
                     @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal twoApproversAbove) {}

  public record Rules(@NotNull @Valid @Size(max = 50) List<Rule> rules) {}

  /** {@code approvals}: 0 (automatic), 1 or 2; {@code reason}: the rule in words, shown on the invoice. */
  public record Requirement(int approvals, String reason) {}

  private final ApprovalPolicyRepository policies;
  private final AuditService audit;
  private final Clock clock;

  public Requirement required(UUID organizationId, String currency, BigDecimal total) {
    if (currency == null || total == null) return new Requirement(1, "One approver (no currency or total to apply a rule to)");
    var policy = policies.findByOrganizationIdAndCurrency(organizationId, currency).orElse(null);
    if (policy == null) return new Requirement(1, "One approver (no approval rule for " + currency + ")");
    BigDecimal amount = total.abs();
    if (policy.getTwoApproversAbove() != null && amount.compareTo(policy.getTwoApproversAbove()) > 0) {
      return new Requirement(2, "Two approvers: " + fmt(amount, currency) + " is above " + fmt(policy.getTwoApproversAbove(), currency));
    }
    if (policy.getAutoApproveBelow() != null && amount.compareTo(policy.getAutoApproveBelow()) < 0) {
      return new Requirement(0, "Approved automatically: " + fmt(amount, currency) + " is below " + fmt(policy.getAutoApproveBelow(), currency));
    }
    return new Requirement(1, "One approver for " + fmt(amount, currency));
  }

  @Transactional(readOnly = true)
  public Rules list(AuthPrincipal me) {
    return new Rules(policies.findByOrganizationIdOrderByCurrencyAsc(me.organizationId()).stream()
        .map(p -> new Rule(p.getCurrency(), p.getAutoApproveBelow(), p.getTwoApproversAbove())).toList());
  }

  /** Replaces the organisation's rules (admins only). */
  @Transactional
  public Rules replace(Rules rules, AuthPrincipal me) {
    if (me.role() != Role.ADMIN) throw ApiException.forbidden("Only admins can change approval rules");
    Set<String> seen = new HashSet<>();
    for (Rule r : rules.rules()) {
      String c = r.currency().toUpperCase(Locale.ROOT);
      if (!seen.add(c)) throw ApiException.badRequest("duplicate_currency", "Each currency can have one rule (" + c + " twice)");
      if (r.autoApproveBelow() != null && r.twoApproversAbove() != null && r.autoApproveBelow().compareTo(r.twoApproversAbove()) > 0) {
        throw ApiException.badRequest("invalid_rule", c + ": the automatic limit must not be above the two-approver limit");
      }
    }
    policies.deleteByOrganizationId(me.organizationId());
    policies.flush();
    Instant now = Instant.now(clock);
    for (Rule r : rules.rules()) {
      ApprovalPolicyEntity p = new ApprovalPolicyEntity();
      p.setOrganizationId(me.organizationId());
      p.setCurrency(r.currency().toUpperCase(Locale.ROOT));
      p.setAutoApproveBelow(r.autoApproveBelow());
      p.setTwoApproversAbove(r.twoApproversAbove());
      p.setUpdatedBy(me.userId());
      p.setUpdatedAt(now);
      policies.save(p);
    }
    audit.log(me.organizationId(), me.userId(), "APPROVAL_RULES_CHANGED", AuditService.ORGANIZATION, me.organizationId(),
        Map.of("rules", rules.rules().stream().map(r -> r.currency().toUpperCase(Locale.ROOT) + ": auto below "
            + r.autoApproveBelow() + ", two above " + r.twoApproversAbove()).toList()));
    return list(me);
  }

  private static String fmt(BigDecimal v, String currency) {
    return v.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency;
  }
}
