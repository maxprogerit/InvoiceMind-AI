package com.invoicemind.ai.firm;

import com.invoicemind.ai.user.OrganizationEntity;
import com.invoicemind.ai.user.OrganizationRepository;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides which client companies a person may work in, and with which role. The only place that answers this question:
 * sign-in, refresh, switching and the listing of companies all go through here.
 *
 * <p>A person may work in a company when they hold a {@code user_org_access} row for it (the role is the row's), or when
 * they are FIRM_ADMIN of the firm that owns it (then they are ADMIN there), or when it is their home company
 * ({@code users.organization_id}; rows written before firms existed fall back to {@code users.role}).
 */
@Service
@RequiredArgsConstructor
public class OrgAccessService {
  /** What a session needs to know once a company is chosen. {@code firmId}/{@code firmRole} are the person's own firm place. */
  public record Context(UUID organizationId, String organizationName, Role role, UUID firmId, FirmRole firmRole) {}

  public record Option(UUID organizationId, String name, Role role, boolean home, boolean active) {}

  private final OrganizationRepository organizations;
  private final UserOrgAccessRepository access;
  private final FirmMemberRepository members;

  @Transactional(readOnly = true)
  public Optional<Context> contextFor(UserEntity user, UUID organizationId) {
    if (organizationId == null) return Optional.empty();
    Optional<OrganizationEntity> found = organizations.findById(organizationId);
    if (found.isEmpty()) return Optional.empty();
    OrganizationEntity org = found.get();
    boolean home = organizationId.equals(user.getOrganizationId());
    // A deactivated client cannot be entered; the one exception is a person's own home company, so nobody is locked out.
    if (!org.isActive() && !home) return Optional.empty();

    Optional<Role> role = roleIn(user, org, home);
    if (role.isEmpty()) return Optional.empty();
    List<FirmMemberEntity> memberships = members.findByUserId(user.getId());
    Optional<FirmMemberEntity> firm = memberships.stream().filter(m -> m.getFirmId().equals(org.getFirmId())).findFirst()
        .or(() -> memberships.stream().findFirst());
    return Optional.of(new Context(org.getId(), org.getName(), role.get(),
        firm.map(FirmMemberEntity::getFirmId).orElse(null), firm.map(FirmMemberEntity::getFirmRole).orElse(null)));
  }

  private Optional<Role> roleIn(UserEntity user, OrganizationEntity org, boolean home) {
    Optional<UserOrgAccessEntity> row = access.findByUserIdAndOrganizationId(user.getId(), org.getId());
    if (row.isPresent()) return Optional.of(row.get().getRole());
    if (org.getFirmId() != null && isFirmAdmin(user.getId(), org.getFirmId())) return Optional.of(Role.ADMIN);
    return home ? Optional.of(user.getRole()) : Optional.empty();
  }

  public boolean isFirmAdmin(UUID userId, UUID firmId) {
    return members.findByFirmIdAndUserId(firmId, userId).map(m -> m.getFirmRole() == FirmRole.FIRM_ADMIN).orElse(false);
  }

  /** Every company the person may switch to, with the role they would hold there, by name. */
  @Transactional(readOnly = true)
  public List<Option> optionsFor(UserEntity user) {
    Map<UUID, Role> roles = new HashMap<>();
    for (UserOrgAccessEntity a : access.findByUserId(user.getId())) roles.put(a.getOrganizationId(), a.getRole());
    for (FirmMemberEntity m : members.findByUserId(user.getId())) {
      if (m.getFirmRole() != FirmRole.FIRM_ADMIN) continue;
      for (OrganizationEntity o : organizations.findByFirmIdOrderByNameAsc(m.getFirmId())) roles.putIfAbsent(o.getId(), Role.ADMIN);
    }
    roles.putIfAbsent(user.getOrganizationId(), user.getRole());
    return organizations.findAllById(roles.keySet()).stream()
        .filter(o -> o.isActive() || o.getId().equals(user.getOrganizationId()))
        .map(o -> new Option(o.getId(), o.getName(), roles.get(o.getId()), o.getId().equals(user.getOrganizationId()), o.isActive()))
        .sorted(Comparator.comparing(Option::name, String.CASE_INSENSITIVE_ORDER)).toList();
  }

  /** Which of these users may act in the organisation (used to put names on audit entries). */
  @Transactional(readOnly = true)
  public Set<UUID> withAccessAmong(UUID organizationId, Collection<UUID> userIds) {
    if (userIds.isEmpty()) return Set.of();
    Set<UUID> result = new HashSet<>(access.findUserIdsWithAccessAmong(organizationId, userIds));
    organizations.findById(organizationId).map(OrganizationEntity::getFirmId).ifPresent(firmId -> {
      for (FirmMemberEntity m : members.findByFirmId(firmId)) {
        if (m.getFirmRole() == FirmRole.FIRM_ADMIN && userIds.contains(m.getUserId())) result.add(m.getUserId());
      }
    });
    return result;
  }
}
