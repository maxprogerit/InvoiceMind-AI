package com.invoicemind.ai.auth;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.auth.AuthDtos.AuthResponse;
import com.invoicemind.ai.auth.AuthDtos.LoginRequest;
import com.invoicemind.ai.auth.AuthDtos.RegisterRequest;
import com.invoicemind.ai.auth.AuthDtos.UserDto;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.firm.FirmRole;
import com.invoicemind.ai.firm.FirmService;
import com.invoicemind.ai.firm.OrgAccessService;
import com.invoicemind.ai.firm.UserOrgAccessEntity;
import com.invoicemind.ai.firm.UserOrgAccessRepository;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.security.JwtService;
import com.invoicemind.ai.user.OrganizationEntity;
import com.invoicemind.ai.user.OrganizationRepository;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
  /** Result of an authentication step: JSON body for the client plus the raw refresh token for the cookie. */
  public record Session(AuthResponse response, String refreshToken) {}

  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository users;
  private final OrganizationRepository organizations;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuditService audit;
  private final AppProperties props;
  private final FirmService firmService;
  private final UserOrgAccessRepository accessRows;
  private final OrgAccessService orgAccess;

  /** Hash compared against when the e-mail is unknown, so unknown/known accounts take similar time. */
  private String dummyHash;

  @Transactional
  public Session register(RegisterRequest req) {
    if (users.findByEmailIgnoreCase(req.email().trim()).isPresent()) {
      throw ApiException.conflict("email_taken", "An account with this e-mail already exists");
    }
    OrganizationEntity org = firmService.createFirmOfOne(req.organizationName().trim());
    UserEntity user = newUser(org.getId(), req.email(), req.fullName(), req.password(), Role.ADMIN);
    firmService.addMember(org.getFirmId(), user.getId(), FirmRole.FIRM_ADMIN);
    audit.log(org.getId(), user.getId(), "ORGANIZATION_REGISTERED", AuditService.USER, user.getId(),
        Map.of("organization", org.getName()));
    return startSession(user, null);
  }

  @Transactional
  public Session login(LoginRequest req) {
    UserEntity user = users.findByEmailIgnoreCase(req.email().trim()).orElse(null);
    boolean ok = passwordEncoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash());
    if (user == null || !ok || !user.isEnabled()) {
      throw ApiException.unauthorized("Invalid e-mail or password");
    }
    audit.log(user.getOrganizationId(), user.getId(), "LOGIN", AuditService.USER, user.getId(), null);
    return startSession(user, null);
  }

  /**
   * Rotating refresh: every use revokes the presented token and issues a new one. {@code noRollbackFor}
   * is essential: on token reuse we revoke the user's whole family and then throw 401, and a rollback
   * would silently undo that revocation.
   */
  @Transactional(noRollbackFor = ApiException.class)
  public Session refresh(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) throw ApiException.unauthorized("Session expired");
    RefreshTokenEntity stored = refreshTokens.findByTokenHash(hash(rawToken))
        .orElseThrow(() -> ApiException.unauthorized("Session expired"));
    Instant now = Instant.now();
    if (stored.getRevokedAt() != null) {
      // A rotated-out token is being replayed: assume theft and kill every session of that user.
      refreshTokens.revokeAllForUser(stored.getUserId(), now);
      log.warn("Refresh token reuse detected for user {}", stored.getUserId());
      throw ApiException.unauthorized("Session expired");
    }
    if (stored.getExpiresAt().isBefore(now)) throw ApiException.unauthorized("Session expired");
    UserEntity user = users.findById(stored.getUserId()).filter(UserEntity::isEnabled)
        .orElseThrow(() -> ApiException.unauthorized("Session expired"));
    stored.setRevokedAt(now);
    return startSession(user, stored.getActiveOrganizationId());
  }

  @Transactional
  public void logout(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) return;
    refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> {
      if (t.getRevokedAt() == null) t.setRevokedAt(Instant.now());
    });
  }

  @Transactional(readOnly = true)
  public UserDto me(AuthPrincipal principal) {
    UserEntity user = users.findById(principal.userId()).filter(UserEntity::isEnabled)
        .orElseThrow(() -> ApiException.unauthorized("Account is disabled"));
    // The company comes from the token, but the role and the right to be there are read afresh: a withdrawn
    // access answers 401, which makes the client refresh and land in its home company.
    OrgAccessService.Context ctx = orgAccess.contextFor(user, principal.organizationId())
        .orElseThrow(() -> ApiException.unauthorized("Access to this organization was withdrawn"));
    return toDto(user, ctx);
  }

  public UserEntity newUser(java.util.UUID orgId, String email, String fullName, String rawPassword, Role role) {
    UserEntity user = new UserEntity();
    user.setOrganizationId(orgId);
    user.setEmail(email.trim().toLowerCase());
    user.setFullName(fullName.trim());
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setRole(role);
    user = users.save(user);
    accessRows.save(new UserOrgAccessEntity(user.getId(), orgId, role));
    return user;
  }

  /** A session for a user who has already been authenticated by other means (single sign-on). */
  @Transactional
  public Session startSessionFor(UserEntity user) {
    audit.log(user.getOrganizationId(), user.getId(), "LOGIN_SSO", AuditService.USER, user.getId(), null);
    return startSession(user, null);
  }

  /**
   * Starts a session in {@code requestedOrg}, or in the home company when that is null or the person no longer has
   * access to it (access withdrawn, client deactivated): a refresh then quietly lands in the home company.
   */
  private Session startSession(UserEntity user, java.util.UUID requestedOrg) {
    OrgAccessService.Context ctx = orgAccess.contextFor(user, requestedOrg)
        .or(() -> orgAccess.contextFor(user, user.getOrganizationId()))
        .orElseGet(() -> new OrgAccessService.Context(user.getOrganizationId(), organizationName(user), user.getRole(), null, null));
    AuthPrincipal principal = new AuthPrincipal(user.getId(), ctx.organizationId(), user.getEmail(), ctx.role(),
        ctx.firmId(), ctx.firmRole());
    String access = jwtService.generateAccessToken(principal);

    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    RefreshTokenEntity entity = new RefreshTokenEntity();
    entity.setUserId(user.getId());
    entity.setTokenHash(hash(raw));
    entity.setExpiresAt(Instant.now().plusSeconds(props.jwt().refreshTtlSeconds()));
    entity.setActiveOrganizationId(ctx.organizationId());
    refreshTokens.save(entity);

    return new Session(new AuthResponse(access, jwtService.accessTtlSeconds(), toDto(user, ctx)), raw);
  }

  private String organizationName(UserEntity user) {
    return organizations.findById(user.getOrganizationId()).map(OrganizationEntity::getName).orElse("");
  }

  /** The profile of the person inside the company the session is working in (role and company are the active ones). */
  public static UserDto toDto(UserEntity u, OrgAccessService.Context ctx) {
    return new UserDto(u.getId(), u.getEmail(), u.getFullName(), ctx.role(), ctx.organizationId(), ctx.organizationName(),
        ctx.firmId(), ctx.firmRole());
  }

  /**
   * Switches the session to another client company. The target is checked against the database, never against the
   * token, and "no such company" and "not yours" answer identically so company ids cannot be probed. The new access
   * token carries the new company; the refresh token is told too, so a later refresh stays in it.
   */
  @Transactional
  public AuthResponse switchOrganization(AuthPrincipal me, java.util.UUID targetOrg, String rawRefreshToken) {
    UserEntity user = users.findById(me.userId()).filter(UserEntity::isEnabled)
        .orElseThrow(() -> ApiException.unauthorized("Account is disabled"));
    OrgAccessService.Context ctx = orgAccess.contextFor(user, targetOrg)
        .orElseThrow(() -> ApiException.forbidden("You do not have access to this organization"));

    if (!ctx.organizationId().equals(me.organizationId())) {
      Map<String, Object> details = Map.of("from", me.organizationId().toString(), "to", ctx.organizationId().toString());
      audit.log(me.organizationId(), user.getId(), "ORG_SWITCH", AuditService.ORGANIZATION, ctx.organizationId(), details);
      audit.log(ctx.organizationId(), user.getId(), "ORG_SWITCH", AuditService.ORGANIZATION, ctx.organizationId(), details);
    }
    if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
      refreshTokens.findByTokenHash(hash(rawRefreshToken))
          .filter(t -> t.getUserId().equals(user.getId()) && t.getRevokedAt() == null)
          .ifPresent(t -> t.setActiveOrganizationId(ctx.organizationId()));
    }
    AuthPrincipal principal = new AuthPrincipal(user.getId(), ctx.organizationId(), user.getEmail(), ctx.role(),
        ctx.firmId(), ctx.firmRole());
    return new AuthResponse(jwtService.generateAccessToken(principal), jwtService.accessTtlSeconds(), toDto(user, ctx));
  }

  /** The client companies this person can switch to. */
  @Transactional(readOnly = true)
  public java.util.List<AuthDtos.OrganizationOption> organizations(AuthPrincipal me) {
    UserEntity user = users.findById(me.userId()).filter(UserEntity::isEnabled)
        .orElseThrow(() -> ApiException.unauthorized("Account is disabled"));
    return orgAccess.optionsFor(user).stream()
        .map(o -> new AuthDtos.OrganizationOption(o.organizationId(), o.name(), o.role(), o.home(), o.active())).toList();
  }

  private String dummyHash() {
    if (dummyHash == null) dummyHash = passwordEncoder.encode("not-a-real-password-" + RANDOM.nextLong());
    return dummyHash;
  }

  static String hash(String raw) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
