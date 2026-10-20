package com.invoicemind.ai.user;

import com.invoicemind.ai.auth.AuthService;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.firm.FirmRole;
import com.invoicemind.ai.firm.FirmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a demo organisation with one user per role on first start. The password comes from
 * APP_DEMO_PASSWORD; with the variable empty nothing is seeded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {
  static final String DOMAIN = "demo.invoicemind.local";

  private final AppProperties props;
  private final UserRepository users;
  private final FirmService firmService;
  private final AuthService authService;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String password = props.seed().demoPassword();
    if (password == null || password.isBlank()) {
      log.info("Demo seeding disabled (APP_DEMO_PASSWORD is empty)");
      return;
    }
    if (users.findByEmailIgnoreCase("admin@" + DOMAIN).isPresent()) return;
    OrganizationEntity org = firmService.createFirmOfOne("Demo Company");
    UserEntity admin = authService.newUser(org.getId(), "admin@" + DOMAIN, "Demo Admin", password, Role.ADMIN);
    firmService.addMember(org.getFirmId(), admin.getId(), FirmRole.FIRM_ADMIN);
    authService.newUser(org.getId(), "approver@" + DOMAIN, "Demo Approver", password, Role.APPROVER);
    authService.newUser(org.getId(), "user@" + DOMAIN, "Demo User", password, Role.USER);
    log.info("Seeded demo organisation: admin@{}, approver@{}, user@{}", DOMAIN, DOMAIN, DOMAIN);
  }
}
