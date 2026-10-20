package com.invoicemind.ai.firm;

import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserOrgAccessRepository extends JpaRepository<UserOrgAccessEntity, UserOrgAccessEntity.Key> {
  List<UserOrgAccessEntity> findByUserId(UUID userId);

  List<UserOrgAccessEntity> findByOrganizationId(UUID organizationId);

  List<UserOrgAccessEntity> findByOrganizationIdIn(Collection<UUID> organizationIds);

  Optional<UserOrgAccessEntity> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

  /** The people of one organisation: whoever has been given access to it, with the role they hold there. */
  @Query("""
      select u from UserEntity u, UserOrgAccessEntity a
      where a.organizationId = :org and u.id = a.userId order by u.createdAt asc""")
  List<UserEntity> findUsersWithAccessTo(@Param("org") UUID organizationId);

  @Query("""
      select u from UserEntity u, UserOrgAccessEntity a
      where a.organizationId = :org and u.id = a.userId and a.role in :roles and u.enabled = true""")
  List<UserEntity> findEnabledUsersWithRoleIn(@Param("org") UUID organizationId, @Param("roles") Collection<Role> roles);

  @Query("select count(a) from UserOrgAccessEntity a, UserEntity u "
      + "where a.organizationId = :org and a.role = :role and u.id = a.userId and u.enabled = true")
  long countEnabledWithRole(@Param("org") UUID organizationId, @Param("role") Role role);

  @Query("select a.userId from UserOrgAccessEntity a where a.organizationId = :org and a.userId in :users")
  List<UUID> findUserIdsWithAccessAmong(@Param("org") UUID organizationId, @Param("users") Collection<UUID> userIds);
}
