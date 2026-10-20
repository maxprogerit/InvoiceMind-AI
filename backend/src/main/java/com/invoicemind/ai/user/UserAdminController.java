package com.invoicemind.ai.user;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.auth.AuthDtos;
import com.invoicemind.ai.auth.AuthService;
import com.invoicemind.ai.auth.RefreshTokenRepository;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.firm.UserOrgAccessEntity;
import com.invoicemind.ai.firm.UserOrgAccessRepository;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Team management. Everything is scoped to the caller's organisation and restricted to ADMIN. */
@Tag(name = "Team")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
  private final UserRepository users;
  private final UserOrgAccessRepository accessRows;
  private final AuthService authService;
  private final RefreshTokenRepository refreshTokens;
  private final AuditService audit;

  public record TeamMember(@NotNull UUID id, @NotNull String email, @NotNull String fullName, @NotNull Role role,
                           @NotNull boolean enabled, @NotNull Instant createdAt) {}

  public record CreateUserRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 200) String fullName,
      @NotNull Role role,
      @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_RULE, message = AuthDtos.PASSWORD_MESSAGE) String password) {}

  public record UpdateUserRequest(Role role, Boolean enabled, @Size(min = 1, max = 200) String fullName) {}

  @GetMapping
  public List<TeamMember> list() {
    UUID org = CurrentUser.get().organizationId();
    // everyone who can work in this organisation, each with the role they hold in it (accountants of a firm included)
    Map<UUID, Role> roles = new HashMap<>();
    accessRows.findByOrganizationId(org).forEach(a -> roles.put(a.getUserId(), a.getRole()));
    return accessRows.findUsersWithAccessTo(org).stream()
        .map(u -> toMember(u, roles.getOrDefault(u.getId(), u.getRole()))).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public TeamMember create(@Valid @RequestBody CreateUserRequest req) {
    AuthPrincipal me = CurrentUser.get();
    if (users.findByEmailIgnoreCase(req.email().trim()).isPresent()) {
      throw ApiException.conflict("email_taken", "An account with this e-mail already exists");
    }
    UserEntity user = authService.newUser(me.organizationId(), req.email(), req.fullName(), req.password(), req.role());
    audit.log(me.organizationId(), me.userId(), "USER_CREATED", AuditService.USER, user.getId(),
        Map.of("email", user.getEmail(), "role", user.getRole().name()));
    return toMember(user, user.getRole());
  }

  @PatchMapping("/{id}")
  @Transactional
  public TeamMember update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
    AuthPrincipal me = CurrentUser.get();
    UserOrgAccessEntity row = accessRows.findByUserIdAndOrganizationId(id, me.organizationId())
        .orElseThrow(() -> ApiException.notFound("User"));
    UserEntity user = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    boolean home = me.organizationId().equals(user.getOrganizationId());
    Map<String, Object> changes = new HashMap<>();

    if (req.role() != null && req.role() != row.getRole()) {
      guardAdminChange(me, user, row.getRole(), "change the role of");
      changes.put("role", row.getRole() + " -> " + req.role());
      row.setRole(req.role());
      if (home) user.setRole(req.role());  // the home company's role is also kept on the user row
    }
    if ((req.enabled() != null && req.enabled() != user.isEnabled()
        || req.fullName() != null && !req.fullName().equals(user.getFullName())) && !home) {
      // the account belongs to another organisation (an accountant of the firm): it is not this organisation's to disable or rename
      throw ApiException.forbidden("This account belongs to another organisation; only its role here can be changed");
    }
    if (req.enabled() != null && req.enabled() != user.isEnabled()) {
      guardAdminChange(me, user, row.getRole(), "disable");
      changes.put("enabled", req.enabled());
      user.setEnabled(req.enabled());
      if (!req.enabled()) refreshTokens.revokeAllForUser(user.getId(), Instant.now());
    }
    if (req.fullName() != null && !req.fullName().equals(user.getFullName())) {
      changes.put("fullName", req.fullName());
      user.setFullName(req.fullName().trim());
    }
    if (!changes.isEmpty()) {
      audit.log(me.organizationId(), me.userId(), "USER_UPDATED", AuditService.USER, user.getId(), changes);
    }
    return toMember(user, row.getRole());
  }

  /** Admins cannot lock themselves out, and the organisation must always keep an active admin. */
  private void guardAdminChange(AuthPrincipal me, UserEntity target, Role roleHere, String verb) {
    if (target.getId().equals(me.userId())) {
      throw ApiException.badRequest("self_change", "You cannot " + verb + " your own account");
    }
    if (roleHere == Role.ADMIN && target.isEnabled()
        && accessRows.countEnabledWithRole(me.organizationId(), Role.ADMIN) <= 1) {
      throw ApiException.badRequest("last_admin", "The organisation must keep at least one active admin");
    }
  }

  private static TeamMember toMember(UserEntity u, Role roleHere) {
    return new TeamMember(u.getId(), u.getEmail(), u.getFullName(), roleHere, u.isEnabled(), u.getCreatedAt());
  }
}
