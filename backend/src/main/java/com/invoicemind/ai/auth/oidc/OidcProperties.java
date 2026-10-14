package com.invoicemind.ai.auth.oidc;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Single sign-on with any OpenID Connect provider (Entra ID, Okta, Keycloak, Google Workspace ...) through the
 * authorization-code flow with PKCE.
 *
 * <p>Accounts are linked, never guessed: a user who signs in must already exist (matched by verified e-mail) or, if
 * {@code autoProvisionOrganizationId} is set and the e-mail domain is allowed, is created in that organisation with
 * {@code defaultRole}. The first successful login pins the provider's stable subject to the account.
 */
@ConfigurationProperties(prefix = "app.oidc")
public record OidcProperties(
    @DefaultValue("false") boolean enabled,
    /** Issuer URL; {@code /.well-known/openid-configuration} is read from it. */
    @DefaultValue("") String issuerUri,
    @DefaultValue("") String clientId,
    /** Empty for a public client (PKCE alone). */
    @DefaultValue("") String clientSecret,
    @DefaultValue("openid email profile") String scopes,
    @DefaultValue("Single sign-on") String buttonLabel,
    /** Public base URL of the application (https://invoices.example.com); derived from the request when empty. */
    @DefaultValue("") String publicUrl,
    @DefaultValue("true") boolean requireEmailVerified,
    /** Organisation id new users are created in; empty disables just-in-time provisioning. */
    @DefaultValue("") String autoProvisionOrganizationId,
    @DefaultValue("USER") String defaultRole,
    /** E-mail domains allowed to be provisioned (and nothing else); required for provisioning. */
    @DefaultValue({}) List<String> allowedEmailDomains) {

  public boolean configured() {
    return enabled && !issuerUri.isBlank() && !clientId.isBlank();
  }
}
