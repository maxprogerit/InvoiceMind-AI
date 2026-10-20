package com.invoicemind.ai.security;

import com.invoicemind.ai.firm.FirmRole;
import com.invoicemind.ai.user.Role;
import java.util.UUID;

/**
 * The authenticated caller. Built from the verified JWT; the organisation always comes from here, never from request input.
 * {@code organizationId} and {@code role} describe the client company the session is working in right now; {@code firmId}
 * and {@code firmRole} describe the person's place in an accounting firm and are null for people outside one.
 */
public record AuthPrincipal(UUID userId, UUID organizationId, String email, Role role, UUID firmId, FirmRole firmRole) {
  /** A caller who is not a firm member (and the shape every pre-firm call site and test uses). */
  public AuthPrincipal(UUID userId, UUID organizationId, String email, Role role) {
    this(userId, organizationId, email, role, null, null);
  }
}
