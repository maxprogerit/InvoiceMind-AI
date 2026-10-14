package com.invoicemind.ai.auth.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OidcServiceTest {
  private static Jwt token(List<String> audience) {
    return Jwt.withTokenValue("t").header("alg", "RS256").subject("s").audience(audience)
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
  }

  @Test
  void anIdTokenForAnotherApplicationIsRejected() {
    var validator = OidcService.audienceValidator("invoicemind-client");
    assertThat(validator.validate(token(List.of("invoicemind-client"))).hasErrors()).isFalse();
    assertThat(validator.validate(token(List.of("other-app", "invoicemind-client"))).hasErrors()).isFalse();
    assertThat(validator.validate(token(List.of("some-other-client"))).hasErrors()).isTrue();
    assertThat(validator.validate(token(List.of("INVOICEMIND-CLIENT"))).hasErrors()).isTrue(); // exact match only
  }

  @Test
  void onlySamePathRedirectTargetsSurvive() {
    assertThat(OidcService.safeNext("/approvals?x=1")).isEqualTo("/approvals?x=1");
    for (String bad : new String[]{null, "", "approvals", "//evil.example", "/\\evil.example", "https://evil.example",
        "javascript:alert(1)", "/ok\r\nSet-Cookie: x=y", "/" + "a".repeat(400)}) {
      assertThat(OidcService.safeNext(bad)).as(String.valueOf(bad)).isEqualTo("/invoices");
    }
  }
}
