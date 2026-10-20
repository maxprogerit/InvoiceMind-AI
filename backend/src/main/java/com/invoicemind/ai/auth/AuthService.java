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
  private final UserOrgAccessRepository access;

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
    return startSession(user, org.getName());
  }

  @Transactional
  public Session login(LoginRequest req) {
    UserEntity user = users.findByEmailIgnoreCase(req.email().trim()).orElse(null);
    boolean ok = passwordEncoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash());
    if (user == null || !ok || !user.isEnabled()) {
      throw ApiException.unauthorized("Invalid e-mail or password");
    }
    audit.log(user.getOrganizationId(), user.getId(), "LOGIN", AuditService.USER, user.getId(), null);
    return startSession(user, organizationName(user));
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
    return startSession(user, organizationName(user));
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
    return toDto(user, organizationName(user));
  }

  public UserEntity newUser(java.util.UUID orgId, String email, String fullName, String rawPassword, Role role) {
    UserEntity user = new UserEntity();
    user.setOrganizationId(orgId);
    user.setEmail(email.trim().toLowerCase());
    user.setFullName(fullName.trim());
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setRole(role);
    user = users.save(user);
    access.save(new UserOrgAccessEntity(user.getId(), orgId, role));
    return user;
  }

  /** A session for a user who has already been authenticated by other means (single sign-on). */
  @Transactional
  public Session startSessionFor(UserEntity user) {
    audit.log(user.getOrganizationId(), user.getId(), "LOGIN_SSO", AuditService.USER, user.getId(), null);
    return startSession(user, organizationName(user));
  }

  private Session startSession(UserEntity user, String orgName) {
    AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getOrganizationId(), user.getEmail(), user.getRole());
    String access = jwtService.generateAccessToken(principal);

    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    RefreshTokenEntity entity = new RefreshTokenEntity();
    entity.setUserId(user.getId());
    entity.setTokenHash(hash(raw));
    entity.setExpiresAt(Instant.now().plusSeconds(props.jwt().refreshTtlSeconds()));
    refreshTokens.save(entity);

    return new Session(new AuthResponse(access, jwtService.accessTtlSeconds(), toDto(user, orgName)), raw);
  }

  private String organizationName(UserEntity user) {
    return organizations.findById(user.getOrganizationId()).map(OrganizationEntity::getName).orElse("");
  }

  public static UserDto toDto(UserEntity u, String orgName) {
    return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getOrganizationId(), orgName);
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
