package com.invoicemind.ai.firm;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.auth.AuthService;
import com.invoicemind.ai.auth.RefreshTokenRepository;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.firm.FirmDtos.ClientAccess;
import com.invoicemind.ai.firm.FirmDtos.ClientRef;
import com.invoicemind.ai.firm.FirmDtos.CreateMemberRequest;
import com.invoicemind.ai.firm.FirmDtos.FirmMemberDto;
import com.invoicemind.ai.firm.FirmDtos.FirmTeam;
import com.invoicemind.ai.firm.FirmService.Membership;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.OrganizationEntity;
import com.invoicemind.ai.user.OrganizationRepository;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.user.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The firm's team: its accountants and which client companies each may work in. Firm admins only. A person has one home
 * company (the first client they were given); the other clients are reached through access rows.
 */
@Service
@RequiredArgsConstructor
public class FirmTeamService {
  private final FirmService firmService;
  private final FirmMemberRepository members;
  private final UserOrgAccessRepository accessRows;
  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final AuthService authService;
  private final RefreshTokenRepository refreshTokens;
  private final AuditService audit;

  @Transactional(readOnly = true)
  public FirmTeam team(AuthPrincipal me) {
    Membership m = firmService.requireMember(me, true);
    List<OrganizationEntity> clients = organizations.findByFirmIdOrderByNameAsc(m.firmId());
    Set<UUID> clientIds = clients.stream().map(OrganizationEntity::getId).collect(Collectors.toSet());
    Map<UUID, List<ClientAccess>> access = accessRows.findByOrganizationIdIn(clientIds).stream().collect(
        Collectors.groupingBy(UserOrgAccessEntity::getUserId,
            Collectors.mapping(a -> new ClientAccess(a.getOrganizationId(), a.getRole()), Collectors.toList())));
    List<FirmMemberDto> out = members.findByFirmId(m.firmId()).stream().map(fm -> users.findById(fm.getUserId())
            .map(u -> new FirmMemberDto(u.getId(), u.getEmail(), u.getFullName(), fm.getFirmRole(), u.isEnabled(),
                u.getOrganizationId(), access.getOrDefault(u.getId(), List.of())))
            .orElse(null))
        .filter(x -> x != null).sorted(Comparator.comparing(FirmMemberDto::fullName, String.CASE_INSENSITIVE_ORDER)).toList();
    return new FirmTeam(clients.stream().map(o -> new ClientRef(o.getId(), o.getName(), o.isActive())).toList(), out);
  }

  /** Creates the account of a new team member with access to at least one client; the first one becomes the home company. */
  @Transactional
  public FirmMemberDto add(AuthPrincipal me, CreateMemberRequest req) {
    Membership m = firmService.requireMember(me, true);
    if (users.findByEmailIgnoreCase(req.email().trim()).isPresent()) {
      throw ApiException.conflict("email_taken", "An account with this e-mail already exists");
    }
    for (ClientAccess a : req.access()) requireActiveClient(m, a.organizationId());
    if (req.access().stream().map(ClientAccess::organizationId).distinct().count() != req.access().size()) {
      throw ApiException.badRequest("duplicate_client", "A client is listed twice");
    }
    ClientAccess home = req.access().get(0);
    UserEntity user = authService.newUser(home.organizationId(), req.email(), req.fullName(), req.password(), home.role());
    for (ClientAccess a : req.access().subList(1, req.access().size())) {
      accessRows.save(new UserOrgAccessEntity(user.getId(), a.organizationId(), a.role()));
    }
    members.save(new FirmMemberEntity(m.firmId(), user.getId(), req.firmRole()));
    for (ClientAccess a : req.access()) {
      audit.log(a.organizationId(), me.userId(), "ACCESS_GRANTED", AuditService.USER, user.getId(),
          Map.of("email", user.getEmail(), "role", a.role().name(), "firmRole", req.firmRole().name()));
    }
    return new FirmMemberDto(user.getId(), user.getEmail(), user.getFullName(), req.firmRole(), true, home.organizationId(),
        req.access());
  }

  /** Gives a team member access to a client, or changes the role they have there. */
  @Transactional
  public ClientAccess grant(AuthPrincipal me, UUID userId, UUID organizationId, Role role) {
    Membership m = firmService.requireMember(me, true);
    requireMemberOf(m, userId);
    requireActiveClient(m, organizationId);
    var existing = accessRows.findByUserIdAndOrganizationId(userId, organizationId);
    UserEntity user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Member"));
    if (existing.isPresent()) {
      if (existing.get().getRole() != role) {
        audit.log(organizationId, me.userId(), "ACCESS_CHANGED", AuditService.USER, userId,
            Map.of("email", user.getEmail(), "role", existing.get().getRole() + " -> " + role));
        existing.get().setRole(role);
        if (organizationId.equals(user.getOrganizationId())) user.setRole(role);  // the home company's role is also kept on the user
      }
    } else {
      accessRows.save(new UserOrgAccessEntity(userId, organizationId, role));
      audit.log(organizationId, me.userId(), "ACCESS_GRANTED", AuditService.USER, userId,
          Map.of("email", user.getEmail(), "role", role.name()));
    }
    return new ClientAccess(organizationId, role);
  }

  /** Takes a client away from a team member. Tokens already issued keep working until they expire (15 minutes). */
  @Transactional
  public void revoke(AuthPrincipal me, UUID userId, UUID organizationId) {
    Membership m = firmService.requireMember(me, true);
    requireMemberOf(m, userId);
    firmService.assertOrgBelongsToFirm(m.firmId(), organizationId);
    UserEntity user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Member"));
    if (organizationId.equals(user.getOrganizationId())) {
      throw ApiException.conflict("home_organization",
          "This is the person's home company; remove them from the firm instead, or give them another home first");
    }
    UserOrgAccessEntity row = accessRows.findByUserIdAndOrganizationId(userId, organizationId)
        .orElseThrow(() -> ApiException.notFound("Access"));
    accessRows.delete(row);
    audit.log(organizationId, me.userId(), "ACCESS_REVOKED", AuditService.USER, userId, Map.of("email", user.getEmail()));
  }

  /** Removes a person from the firm: no more firm role, no more access to its clients, the account is disabled. */
  @Transactional
  public void remove(AuthPrincipal me, UUID userId) {
    Membership m = firmService.requireMember(me, true);
    FirmMemberEntity target = requireMemberOf(m, userId);
    if (userId.equals(me.userId())) throw ApiException.badRequest("self_change", "You cannot remove yourself from the firm");
    if (target.getFirmRole() == FirmRole.FIRM_ADMIN && members.findByFirmId(m.firmId()).stream()
        .filter(x -> x.getFirmRole() == FirmRole.FIRM_ADMIN).count() <= 1) {
      throw ApiException.badRequest("last_admin", "The firm must keep at least one administrator");
    }
    UserEntity user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Member"));
    List<OrganizationEntity> clients = organizations.findByFirmIdOrderByNameAsc(m.firmId());
    for (UserOrgAccessEntity a : accessRows.findByOrganizationIdIn(clients.stream().map(OrganizationEntity::getId).toList())) {
      if (!a.getUserId().equals(userId)) continue;
      accessRows.delete(a);
      audit.log(a.getOrganizationId(), me.userId(), "ACCESS_REVOKED", AuditService.USER, userId, Map.of("email", user.getEmail()));
    }
    members.delete(target);
    user.setEnabled(false);
    refreshTokens.revokeAllForUser(userId, Instant.now());
  }

  private FirmMemberEntity requireMemberOf(Membership m, UUID userId) {
    return members.findByFirmIdAndUserId(m.firmId(), userId).orElseThrow(() -> ApiException.notFound("Member"));
  }

  private void requireActiveClient(Membership m, UUID organizationId) {
    OrganizationEntity client = firmService.assertOrgBelongsToFirm(m.firmId(), organizationId);
    if (!client.isActive()) throw ApiException.conflict("client_inactive", "This client is deactivated");
  }
}
