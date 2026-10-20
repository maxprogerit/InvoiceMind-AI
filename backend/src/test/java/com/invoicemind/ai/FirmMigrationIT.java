package com.invoicemind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * V8 (firms) applied to a database that holds data written under V1-V7: nothing may be lost, every company becomes a
 * firm of one, everybody keeps their access. Uses Flyway directly on a throw-away Postgres (no application context).
 */
@Testcontainers
class FirmMigrationIT {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
      DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  private static Flyway flyway(String target) {
    return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration").target(target).load();
  }

  @Test
  void v8BackfillsFirmsAndAccessFromV7Data() throws Exception {
    flyway("7").migrate();
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    var jdbc = new JdbcTemplate(ds);

    UUID acme = UUID.randomUUID(), globex = UUID.randomUUID();
    insertOrg(jdbc, acme, "Acme GmbH");
    insertOrg(jdbc, globex, "Globex");
    UUID acmeAdmin = insertUser(jdbc, acme, "admin@acme.test", "ADMIN", true);
    UUID acmeApprover = insertUser(jdbc, acme, "approver@acme.test", "APPROVER", true);
    UUID acmeUser = insertUser(jdbc, acme, "user@acme.test", "USER", true);
    UUID globexAdmin = insertUser(jdbc, globex, "admin@globex.test", "ADMIN", true);
    UUID disabledAdmin = insertUser(jdbc, globex, "old-admin@globex.test", "ADMIN", false);
    jdbc.update("insert into refresh_tokens (id, user_id, token_hash, expires_at) values (?, ?, ?, now() + interval '1 day')",
        UUID.randomUUID(), acmeAdmin, "hash-" + UUID.randomUUID());
    jdbc.update("insert into audit_logs (organization_id, user_id, action, entity_type) values (?, ?, 'LOGIN', 'USER')",
        acme, acmeAdmin);

    try (Connection ignored = ds.getConnection()) {
      flyway("latest").migrate();
    }

    // every company is a firm of one with the same name, and keeps its id
    List<Map<String, Object>> firms = jdbc.queryForList("select id, name, plan, monthly_document_limit from firms order by name");
    assertThat(firms).hasSize(2);
    assertThat(firms).extracting(f -> f.get("name")).containsExactly("Acme GmbH", "Globex");
    assertThat(firms).allSatisfy(f -> {
      assertThat(f.get("plan")).isEqualTo("FREE");
      assertThat(f.get("monthly_document_limit")).isNull();  // unlimited: nothing changes for existing users
    });
    assertThat(jdbc.queryForObject("select count(*) from organizations where firm_id = id and active", Integer.class)).isEqualTo(2);

    // everybody has exactly the access they had: home company, current role
    assertThat(jdbc.queryForObject("select count(*) from user_org_access", Integer.class)).isEqualTo(5);
    assertThat(jdbc.queryForObject("select count(*) from user_org_access a join users u on u.id = a.user_id "
        + "where a.organization_id = u.organization_id and a.role = u.role", Integer.class)).isEqualTo(5);
    assertThat(roleOf(jdbc, acmeApprover, acme)).isEqualTo("APPROVER");
    assertThat(roleOf(jdbc, acmeUser, acme)).isEqualTo("USER");

    // the admins (and only they) become FIRM_ADMIN of their firm of one
    List<Map<String, Object>> members = jdbc.queryForList("select firm_id, user_id, firm_role from firm_members");
    assertThat(members).extracting(m -> m.get("user_id")).containsExactlyInAnyOrder(acmeAdmin, globexAdmin, disabledAdmin);
    assertThat(members).allSatisfy(m -> assertThat(m.get("firm_role")).isEqualTo("FIRM_ADMIN"));
    assertThat(jdbc.queryForObject("select count(*) from firm_members m join users u on u.id = m.user_id "
        + "where m.firm_id = u.organization_id", Integer.class)).isEqualTo(3);

    // pre-existing rows are untouched
    assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(5);
    assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where active_organization_id is null", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from audit_logs", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, acmeAdmin)).isEqualTo("admin@acme.test");
  }

  private static void insertOrg(JdbcTemplate jdbc, UUID id, String name) {
    jdbc.update("insert into organizations (id, name, ingest_token) values (?, ?, ?)", id, name, id.toString().replace("-", "").substring(0, 24));
  }

  private static UUID insertUser(JdbcTemplate jdbc, UUID org, String email, String role, boolean enabled) {
    UUID id = UUID.randomUUID();
    jdbc.update("insert into users (id, organization_id, email, password_hash, full_name, role, enabled) values (?, ?, ?, 'x', ?, ?, ?)",
        id, org, email, email, role, enabled);
    return id;
  }

  private static String roleOf(JdbcTemplate jdbc, UUID user, UUID org) {
    return jdbc.queryForObject("select role from user_org_access where user_id = ? and organization_id = ?", String.class, user, org);
  }
}
