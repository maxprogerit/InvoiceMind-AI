package com.invoicemind.ai.security;

import com.invoicemind.ai.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
  private CurrentUser() {}

  public static AuthPrincipal get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
      throw ApiException.unauthorized("Authentication required");
    }
    return principal;
  }
}
