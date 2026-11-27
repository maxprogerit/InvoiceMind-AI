package com.invoicemind.ai.ingest;

import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.user.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The per-organisation service account invoices from outside senders are attributed to ("E-mail intake"). It cannot
 * sign in (disabled, no usable password), has no access row (so it is not in the team) and never approves anything;
 * every person of the organisation can therefore approve what it brought in.
 */
@Component
@RequiredArgsConstructor
public class IntakeAccounts {
  private final UserRepository users;

  public static String emailFor(UUID organizationId) {
    return "intake+" + organizationId + "@system.invoicemind.invalid";
  }

  @Transactional
  public UserEntity forOrganization(UUID organizationId) {
    return users.findByEmailIgnoreCase(emailFor(organizationId)).orElseGet(() -> {
      UserEntity u = new UserEntity();
      u.setOrganizationId(organizationId);
      u.setEmail(emailFor(organizationId));
      u.setFullName("E-mail intake");
      u.setPasswordHash("!disabled"); // not a bcrypt hash: no password ever matches
      u.setRole(Role.ACCOUNTANT);
      u.setEnabled(false);
      u.setSystemAccount(true);
      return users.saveAndFlush(u);
    });
  }
}
