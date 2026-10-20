package com.invoicemind.ai.firm;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.firm.FirmDtos.ClientSummary;
import com.invoicemind.ai.firm.FirmDtos.CreateClientRequest;
import com.invoicemind.ai.firm.FirmDtos.FirmInfo;
import com.invoicemind.ai.firm.FirmDtos.UpdateClientRequest;
import com.invoicemind.ai.fx.ExchangeRateService;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.OrganizationEntity;
import com.invoicemind.ai.user.OrganizationRepository;
import com.invoicemind.ai.user.Role;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Firms and their client companies. Who the caller is in the firm is read from the database on every call (the token's
 * firm claims are a hint for the client, not an authorisation), and every client id from a request is checked against the
 * caller's firm with {@link #assertOrgBelongsToFirm}.
 */
@Service
@RequiredArgsConstructor
public class FirmService {
  /** The caller's place in a firm, from the database. */
  public record Membership(UUID firmId, UUID userId, FirmRole role) {
    public boolean isAdmin() { return role == FirmRole.FIRM_ADMIN; }
  }

  private final FirmRepository firms;
  private final FirmMemberRepository members;
  private final OrganizationRepository organizations;
  private final UserOrgAccessRepository accessRows;
  private final AuditService audit;
  private final ExchangeRateService fx;
  private final NamedParameterJdbcTemplate jdbc;
  private final Clock clock;

  // ------------------------------------------------------------ provisioning
  /** A company that signs up on its own is a firm with one client: itself. Joins the caller's transaction. */
  @Transactional
  public OrganizationEntity createFirmOfOne(String name) {
    FirmEntity firm = firms.save(new FirmEntity(name));
    OrganizationEntity org = new OrganizationEntity(name);
    org.setFirmId(firm.getId());
    return organizations.save(org);
  }

  @Transactional
  public void addMember(UUID firmId, UUID userId, FirmRole role) {
    members.save(new FirmMemberEntity(firmId, userId, role));
  }

  // ------------------------------------------------------------ access checks
  /** The caller's firm membership; 403 for everyone who is not in a firm (or, with {@code adminOnly}, not its admin). */
  @Transactional(readOnly = true)
  public Membership requireMember(AuthPrincipal me, boolean adminOnly) {
    List<FirmMemberEntity> mine = members.findByUserId(me.userId());
    FirmMemberEntity m = mine.stream().filter(x -> x.getFirmId().equals(me.firmId())).findFirst()
        .or(() -> mine.stream().findFirst())
        .orElseThrow(() -> ApiException.forbidden("This area is for members of an accounting firm"));
    if (adminOnly && m.getFirmRole() != FirmRole.FIRM_ADMIN) {
      throw ApiException.forbidden("Only firm administrators can do this");
    }
    return new Membership(m.getFirmId(), m.getUserId(), m.getFirmRole());
  }

  /** A client of the firm, or 404 (a foreign id and a missing one look the same). */
  @Transactional(readOnly = true)
  public OrganizationEntity assertOrgBelongsToFirm(UUID firmId, UUID organizationId) {
    return organizations.findByIdAndFirmId(organizationId, firmId).orElseThrow(() -> ApiException.notFound("Client"));
  }

  // ------------------------------------------------------------ firm and clients
  @Transactional(readOnly = true)
  public FirmInfo info(AuthPrincipal me) {
    Membership m = requireMember(me, false);
    FirmEntity firm = firms.findById(m.firmId()).orElseThrow(() -> ApiException.notFound("Firm"));
    return new FirmInfo(firm.getId(), firm.getName(), firm.getPlan(), firm.getMonthlyDocumentLimit(),
        documentsThisMonth(firm.getId()), m.role(), organizations.findByFirmIdOrderByNameAsc(firm.getId()).size());
  }

  /** Firm admins see every client; accountants the clients they have been given access to. */
  @Transactional(readOnly = true)
  public List<ClientSummary> clients(AuthPrincipal me) {
    Membership m = requireMember(me, false);
    List<OrganizationEntity> all = organizations.findByFirmIdOrderByNameAsc(m.firmId());
    Set<UUID> visible = new HashSet<>();
    if (m.isAdmin()) {
      all.forEach(o -> visible.add(o.getId()));
    } else {
      accessRows.findByUserId(me.userId()).forEach(a -> visible.add(a.getOrganizationId()));
    }
    List<OrganizationEntity> shown = all.stream()
        .filter(o -> visible.contains(o.getId()) && (o.isActive() || m.isAdmin())).toList();
    Map<UUID, Counts> counts = summarise(shown.stream().map(OrganizationEntity::getId).toList());
    return shown.stream().map(o -> toSummary(o, counts.getOrDefault(o.getId(), Counts.EMPTY))).toList();
  }

  @Transactional
  public ClientSummary createClient(AuthPrincipal me, CreateClientRequest req) {
    Membership m = requireMember(me, true);
    String name = req.name().trim();
    if (nameTaken(m.firmId(), name, null)) {
      throw ApiException.conflict("client_exists", "The firm already has a client with this name");
    }
    OrganizationEntity org = new OrganizationEntity(name);
    org.setFirmId(m.firmId());
    org.setBaseCurrency(isoCode(req.baseCurrency()));
    org = organizations.save(org);
    accessRows.save(new UserOrgAccessEntity(me.userId(), org.getId(), Role.ADMIN));  // the creator can work in it right away
    audit.log(org.getId(), me.userId(), "CLIENT_CREATED", AuditService.ORGANIZATION, org.getId(),
        Map.of("name", name, "baseCurrency", org.getBaseCurrency()));
    return toSummary(org, Counts.EMPTY);
  }

  @Transactional
  public ClientSummary updateClient(AuthPrincipal me, UUID id, UpdateClientRequest req) {
    Membership m = requireMember(me, true);
    OrganizationEntity org = assertOrgBelongsToFirm(m.firmId(), id);
    Map<String, Object> changes = new HashMap<>();
    if (req.name() != null && !req.name().trim().equals(org.getName())) {
      String name = req.name().trim();
      if (nameTaken(m.firmId(), name, id)) {
        throw ApiException.conflict("client_exists", "The firm already has a client with this name");
      }
      changes.put("name", org.getName() + " -> " + name);
      org.setName(name);
    }
    // the currency change audits itself and clears the analytics cache of the client
    if (req.baseCurrency() != null) fx.setBaseCurrency(id, me.userId(), req.baseCurrency());
    if (!changes.isEmpty()) audit.log(id, me.userId(), "CLIENT_UPDATED", AuditService.ORGANIZATION, id, changes);
    org = organizations.save(org);
    return toSummary(org, summarise(List.of(id)).getOrDefault(id, Counts.EMPTY));
  }

  /** Deactivating hides the client from switching; its data is kept. The client you are working in cannot be deactivated. */
  @Transactional
  public ClientSummary setActive(AuthPrincipal me, UUID id, boolean active) {
    Membership m = requireMember(me, true);
    OrganizationEntity org = assertOrgBelongsToFirm(m.firmId(), id);
    if (org.isActive() != active) {
      if (!active) {
        if (id.equals(me.organizationId())) {
          throw ApiException.conflict("client_in_use", "Switch to another client before deactivating this one");
        }
        if (organizations.findByFirmIdOrderByNameAsc(m.firmId()).stream().filter(OrganizationEntity::isActive).count() <= 1) {
          throw ApiException.conflict("last_client", "The firm must keep at least one active client");
        }
      }
      org.setActive(active);
      audit.log(id, me.userId(), active ? "CLIENT_ACTIVATED" : "CLIENT_DEACTIVATED", AuditService.ORGANIZATION, id, null);
    }
    return toSummary(org, summarise(List.of(id)).getOrDefault(id, Counts.EMPTY));
  }

  private boolean nameTaken(UUID firmId, String name, UUID except) {
    return organizations.findByFirmIdOrderByNameAsc(firmId).stream()
        .anyMatch(o -> !o.getId().equals(except) && o.getName().equalsIgnoreCase(name));
  }

  // ------------------------------------------------------------ monthly limit
  /**
   * Refuses an upload that would take the firm past its monthly document limit (all client companies together, calendar
   * month, UTC). Counts the uploads recorded in the audit trail, so deleting a document does not give the quota back.
   * Organisations that belong to no firm, and firms without a limit, are never refused.
   */
  public void assertUploadAllowed(AuthPrincipal me) {
    List<Map<String, Object>> row = jdbc.queryForList("""
        select f.id, f.monthly_document_limit from organizations o join firms f on f.id = o.firm_id where o.id = :org""",
        new MapSqlParameterSource("org", me.organizationId()));
    if (row.isEmpty() || row.get(0).get("monthly_document_limit") == null) return;
    UUID firmId = (UUID) row.get(0).get("id");
    int limit = ((Number) row.get(0).get("monthly_document_limit")).intValue();
    long used = documentsThisMonth(firmId);
    if (used >= limit) {
      audit.logRegardlessOfOutcome(me.organizationId(), me.userId(), "FIRM_LIMIT_REACHED", AuditService.ORGANIZATION,
          me.organizationId(), Map.of("limit", limit, "used", used));
      LocalDate resets = LocalDate.now(clock).withDayOfMonth(1).plusMonths(1);
      throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "firm_limit_reached",
          "Your firm has used its " + limit + " documents for this month (all client companies together). "
              + "The limit resets on " + resets + ".");
    }
  }

  @Transactional(readOnly = true)
  public long documentsThisMonth(UUID firmId) {
    LocalDate first = LocalDate.now(clock).withDayOfMonth(1);
    Long n = jdbc.queryForObject("""
        select count(*) from audit_logs a join organizations o on o.id = a.organization_id
        where o.firm_id = :firm and a.action = 'INVOICE_UPLOADED' and a.created_at >= :from and a.created_at < :to""",
        new MapSqlParameterSource().addValue("firm", firmId)
            .addValue("from", Timestamp.from(first.atStartOfDay().toInstant(ZoneOffset.UTC)))
            .addValue("to", Timestamp.from(first.plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC))),
        Long.class);
    return n == null ? 0 : n;
  }

  // ------------------------------------------------------------ summary
  private record Counts(long documents, long inReview, long failed, long awaitingApproval, BigDecimal monthToDate,
                        long otherCurrency) {
    static final Counts EMPTY = new Counts(0, 0, 0, 0, BigDecimal.ZERO, 0);
  }

  /** One grouped pass over the invoices of the given clients (served by ix_invoices_org_status / ix_invoices_org_created). */
  private Map<UUID, Counts> summarise(List<UUID> organizationIds) {
    Map<UUID, Counts> out = new HashMap<>();
    if (organizationIds.isEmpty()) return out;
    LocalDate monthStart = LocalDate.now(clock).withDayOfMonth(1);
    jdbc.query("""
        select i.organization_id,
               count(*) as documents,
               count(*) filter (where i.status = 'NEEDS_REVIEW') as in_review,
               count(*) filter (where i.status = 'FAILED') as failed,
               count(*) filter (where i.status = 'VALIDATED') as awaiting_approval,
               coalesce(sum(i.total) filter (where i.status = 'APPROVED' and i.currency = o.base_currency
                 and coalesce(i.invoice_date, i.created_at::date) >= :monthStart), 0) as month_total,
               count(*) filter (where i.status = 'APPROVED' and i.currency is distinct from o.base_currency
                 and i.total is not null and coalesce(i.invoice_date, i.created_at::date) >= :monthStart) as month_other
        from invoices i join organizations o on o.id = i.organization_id
        where i.organization_id in (:ids)
        group by i.organization_id, o.base_currency""",
        new MapSqlParameterSource().addValue("ids", organizationIds).addValue("monthStart", Date.valueOf(monthStart)),
        rs -> {
          out.put(rs.getObject("organization_id", UUID.class), new Counts(rs.getLong("documents"), rs.getLong("in_review"),
              rs.getLong("failed"), rs.getLong("awaiting_approval"), rs.getBigDecimal("month_total"), rs.getLong("month_other")));
        });
    return out;
  }

  private static ClientSummary toSummary(OrganizationEntity o, Counts c) {
    return new ClientSummary(o.getId(), o.getName(), o.getBaseCurrency(), o.isActive(), c.documents(), c.inReview(),
        c.failed(), c.awaitingApproval(), c.monthToDate().setScale(2, RoundingMode.HALF_UP), c.otherCurrency());
  }

  private static String isoCode(String code) {
    try {
      return java.util.Currency.getInstance(code.trim().toUpperCase(Locale.ROOT)).getCurrencyCode();
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("invalid_currency", "Unknown currency code");
    }
  }
}
