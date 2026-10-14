package com.invoicemind.ai.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  @Query("select u from UserEntity u where lower(u.email) = lower(:email)")
  Optional<UserEntity> findByEmailIgnoreCase(@Param("email") String email);

  long countByOrganizationIdAndRoleAndEnabledTrue(UUID organizationId, Role role);

  List<UserEntity> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

  List<UserEntity> findByOrganizationIdAndRoleInAndEnabledTrue(UUID organizationId, List<Role> roles);

  Optional<UserEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

  Optional<UserEntity> findByOidcIssuerAndOidcSubject(String issuer, String subject);
}
