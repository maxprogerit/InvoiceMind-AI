package com.invoicemind.ai.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  @Query("select u from UserEntity u where lower(u.email) = lower(:email)")
  Optional<UserEntity> findByEmailIgnoreCase(@Param("email") String email);

  Optional<UserEntity> findByOidcIssuerAndOidcSubject(String issuer, String subject);
}
