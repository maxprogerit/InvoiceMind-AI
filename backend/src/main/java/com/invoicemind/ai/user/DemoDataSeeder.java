package com.invoicemind.ai.user;

import com.invoicemind.ai.approval.ApprovalPolicyService;
import com.invoicemind.ai.approval.ApprovalPolicyService.Rule;
import com.invoicemind.ai.approval.ApprovalPolicyService.Rules;
import com.invoicemind.ai.auth.AuthService;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.firm.FirmRole;
import com.invoicemind.ai.firm.FirmService;
import com.invoicemind.ai.security.AuthPrincipal;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the demo organisation on first start: one user per role (two approvers, so the two-approver rule can be
 * shown) and approval rules for EUR, USD and GBP. The password comes from APP_DEMO_PASSWORD; with the variable empty
 * nothing is seeded. No invoices are seeded: demo invoices go through the real pipeline
 * ({@code scripts/demo-flow.cjs} uploads and e-mails the sample documents).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {
  public static final String DOMAIN = "demo.invoicemind.local";
  public static final String ORGANIZATION = "Demo Company";

  /** E-mail prefix, name and role of each demo user. */
  record DemoUser(String local, String name, Role role) {}

  static final List<DemoUser> USERS = List.of(
      new DemoUser("admin", "Dana Admin", Role.ADMIN),
      new DemoUser("accountant", "Alex Accountant", Role.ACCOUNTANT),
      new DemoUser("approver", "Avery Approver", Role.APPROVER),
      new DemoUser("approver2", "Sam Second-Approver", Role.APPROVER));

  /** Automatic below 100, one approver up to 5,000, two approvers above, in each currency. */
  static final List<Rule> RULES = List.of("EUR", "USD", "GBP").stream()
      .map(c -> new Rule(c, new BigDecimal("100.00"), new BigDecimal("5000.00"))).toList();

  private final AppProperties props;
  private final UserRepository users;
  private final FirmService firmService;
  private final AuthService authService;
  private final ApprovalPolicyService approvalPolicies;

  @Override
  public void run(ApplicationArguments args) {
    String password = props.seed().demoPassword();
    if (password == null || password.isBlank()) {
      log.info("Demo seeding disabled (APP_DEMO_PASSWORD is empty)");
      return;
    }
    seed(password);
  }

  /** Idempotent: does nothing when the demo admin exists. Returns whether it seeded. */
  @Transactional
  public boolean seed(String password) {
    if (users.findByEmailIgnoreCase("admin@" + DOMAIN).isPresent()) return false;
    OrganizationEntity org = firmService.createFirmOfOne(ORGANIZATION);
    UserEntity admin = null;
    for (DemoUser u : USERS) {
      UserEntity created = authService.newUser(org.getId(), u.local() + "@" + DOMAIN, u.name(), password, u.role());
      if (u.role() == Role.ADMIN) admin = created;
    }
    firmService.addMember(org.getFirmId(), admin.getId(), FirmRole.FIRM_ADMIN);
    approvalPolicies.replace(new Rules(RULES), new AuthPrincipal(admin.getId(), org.getId(), admin.getEmail(), Role.ADMIN));
    log.info("Seeded demo organisation '{}': {} @{}", ORGANIZATION, USERS.stream().map(DemoUser::local).toList(), DOMAIN);
    return true;
  }
}
