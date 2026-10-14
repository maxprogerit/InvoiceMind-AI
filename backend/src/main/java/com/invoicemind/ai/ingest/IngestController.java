package com.invoicemind.ai.ingest;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.security.CurrentUser;
import com.invoicemind.ai.user.OrganizationEntity;
import com.invoicemind.ai.user.OrganizationRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The e-mail channel of an organisation: its address, a way to rotate it, and what arrived recently. Admin only. */
@Tag(name = "Integrations")
@RestController
@RequestMapping("/api/v1/ingest/email")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class IngestController {
  public record MailMessageDto(@NotNull long id, String from, String subject, @NotNull String status, String reason,
                               @NotNull int invoicesCreated, @NotNull Instant receivedAt) {}

  public record EmailIngestStatus(@NotNull boolean enabled, String address, @NotNull boolean requireAuthResults,
                                  @NotNull List<MailMessageDto> recent) {}

  private final MailProperties props;
  private final OrganizationRepository organizations;
  private final NamedParameterJdbcTemplate jdbc;
  private final AuditService audit;

  @GetMapping
  @Transactional(readOnly = true)
  public EmailIngestStatus status() {
    AuthPrincipal me = CurrentUser.get();
    return status(organizations.findById(me.organizationId()).orElseThrow());
  }

  /** A new token invalidates the old address immediately (use it if the address leaked into the wrong hands). */
  @PostMapping("/token")
  @Transactional
  public EmailIngestStatus rotate() {
    AuthPrincipal me = CurrentUser.get();
    OrganizationEntity org = organizations.findById(me.organizationId()).orElseThrow();
    org.setIngestToken(OrganizationEntity.newIngestToken());
    organizations.save(org);
    audit.log(me.organizationId(), me.userId(), "EMAIL_ADDRESS_ROTATED", AuditService.ORGANIZATION, me.organizationId(), Map.of());
    return status(org);
  }

  private EmailIngestStatus status(OrganizationEntity org) {
    List<MailMessageDto> recent = jdbc.query("""
        select id, from_address, subject, status, reason, invoices_created, created_at from email_ingest_log
        where organization_id = :org order by created_at desc, id desc limit 30""",
        new MapSqlParameterSource("org", org.getId()),
        (rs, i) -> new MailMessageDto(rs.getLong("id"), rs.getString("from_address"), rs.getString("subject"),
            rs.getString("status"), rs.getString("reason"), rs.getInt("invoices_created"),
            rs.getTimestamp("created_at").toInstant()));
    return new EmailIngestStatus(props.configured(), props.configured() ? addressFor(org.getIngestToken()) : null,
        props.requireAuthResults(), recent);
  }

  /** {@code invoices+<token>@example.com} from {@code invoices@example.com}. */
  String addressFor(String token) {
    String base = props.address().trim();
    int at = base.indexOf('@');
    if (at < 1) throw ApiException.badRequest("mail_not_configured", "E-mail ingestion is not configured");
    return base.substring(0, at) + "+" + token + base.substring(at);
  }
}
