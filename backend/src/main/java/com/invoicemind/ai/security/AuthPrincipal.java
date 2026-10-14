package com.invoicemind.ai.security;

import com.invoicemind.ai.user.Role;
import java.util.UUID;

/** The authenticated caller. Built from the verified JWT; the organisation always comes from here, never from request input. */
public record AuthPrincipal(UUID userId, UUID organizationId, String email, Role role) {}
