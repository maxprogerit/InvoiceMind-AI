package com.invoicemind.ai.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicemind.ai.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlGuardTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "http://127.0.0.1/hook", "http://localhost:8080/hook", "http://[::1]/hook", "http://10.0.0.5/hook",
      "http://192.168.1.20/hook", "http://172.16.0.9/hook", "http://169.254.169.254/latest/meta-data/",
      "http://100.64.0.1/hook", "http://0.0.0.0/hook", "http://[fd00::1]/hook", "http://[fe80::1]/hook",
      "http://224.0.0.1/hook", "http://[::ffff:127.0.0.1]/hook"})
  void internalAddressesAreRefused(String url) {
    assertThatThrownBy(() -> UrlGuard.check(url, false))
        .isInstanceOf(ApiException.class).hasMessageContaining("private or internal");
  }

  @ParameterizedTest
  @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com/x", "gopher://example.com", "javascript:alert(1)",
      "//example.com/hook", "not a url", "http://", "https://user:secret@example.com/hook"})
  void otherSchemesAndCredentialsAreRefused(String url) {
    assertThatThrownBy(() -> UrlGuard.check(url, false)).isInstanceOf(ApiException.class);
  }

  @Test
  void unresolvableHostsAreRefused() {
    assertThatThrownBy(() -> UrlGuard.check("https://definitely-not-a-real-host.invalid/hook", false))
        .isInstanceOf(ApiException.class).hasMessageContaining("cannot be resolved");
  }

  @Test
  void publicAddressesPass() {
    assertThat(UrlGuard.check("https://93.184.216.34/hook", false).getHost()).isEqualTo("93.184.216.34");
    assertThat(UrlGuard.check("  http://8.8.8.8:8443/a/b?c=d  ", false).getPort()).isEqualTo(8443);
  }

  @Test
  void theSwitchAllowsInternalTargetsForSelfHostedReceivers() {
    assertThat(UrlGuard.check("http://10.1.2.3/hook", true).getHost()).isEqualTo("10.1.2.3");
    // the scheme and credential rules apply either way
    assertThatThrownBy(() -> UrlGuard.check("file:///etc/passwd", true)).isInstanceOf(ApiException.class);
  }
}
