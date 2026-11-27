package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.invoicemind.ai.support.AbstractIntegrationTest;
import com.invoicemind.ai.support.TestApi.Session;
import com.invoicemind.ai.user.DemoDataSeeder;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The demo organisation: one user per role, approval rules, and nothing seeded twice. */
class DemoSeedIT extends AbstractIntegrationTest {
  @Autowired
  DemoDataSeeder seeder;

  @Test
  void seedsOneUserPerRoleAndTheApprovalRulesOnce() {
    String password = "Demo-seed-pass-1!";
    assertThat(seeder.seed(password)).isTrue();
    assertThat(seeder.seed(password)).isFalse();

    Map<String, String> roles = Map.of("admin", "ADMIN", "accountant", "ACCOUNTANT", "approver", "APPROVER", "approver2", "APPROVER");
    Session admin = null;
    for (var e : roles.entrySet()) {
      Session s = api.login(e.getKey() + "@" + DemoDataSeeder.DOMAIN, password);
      JsonNode me = api.get("/api/v1/auth/me", s.token()).json();
      assertThat(me.get("role").asText()).as(e.getKey()).isEqualTo(e.getValue());
      if (e.getKey().equals("admin")) admin = s;
    }

    JsonNode rules = api.get("/api/v1/approval-rules", admin.token()).json().get("rules");
    assertThat(rules.findValuesAsText("currency")).containsExactly("EUR", "GBP", "USD");
    assertThat(rules.get(0).get("autoApproveBelow").decimalValue()).isEqualByComparingTo("100");
    assertThat(rules.get(0).get("twoApproversAbove").decimalValue()).isEqualByComparingTo("5000");
  }
}
