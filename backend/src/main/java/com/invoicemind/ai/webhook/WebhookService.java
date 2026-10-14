package com.invoicemind.ai.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.webhook.WebhookDtos.CreateEndpointRequest;
import com.invoicemind.ai.webhook.WebhookDtos.CreatedEndpointDto;
import com.invoicemind.ai.webhook.WebhookDtos.DeliveryDto;
import com.invoicemind.ai.webhook.WebhookDtos.EndpointDto;
import com.invoicemind.ai.webhook.WebhookDtos.UpdateEndpointRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Endpoint management for admins. Everything is scoped to the caller's organisation. */
@Service
@RequiredArgsConstructor
public class WebhookService {
  static final int MAX_ENDPOINTS = 10;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final NamedParameterJdbcTemplate jdbc;
  private final WebhookProperties props;
  private final AuditService audit;
  private final ObjectMapper mapper;

  @Transactional(readOnly = true)
  public List<EndpointDto> list(UUID org) {
    return jdbc.query("select * from webhook_endpoints where organization_id = :org order by created_at",
        new MapSqlParameterSource("org", org), (rs, i) -> toDto(rs));
  }

  @Transactional
  public CreatedEndpointDto create(AuthPrincipal me, CreateEndpointRequest req) {
    Integer count = jdbc.queryForObject("select count(*) from webhook_endpoints where organization_id = :org",
        new MapSqlParameterSource("org", me.organizationId()), Integer.class);
    if (count != null && count >= MAX_ENDPOINTS) {
      throw ApiException.conflict("too_many_webhooks", "An organisation can have at most " + MAX_ENDPOINTS + " webhooks");
    }
    String url = UrlGuard.check(req.url(), props.allowPrivateTargets()).toString();
    String events = events(req.events());
    UUID id = UUID.randomUUID();
    String secret = "whsec_" + randomToken();
    jdbc.update("""
        insert into webhook_endpoints (id, organization_id, url, description, secret, events)
        values (:id, :org, :url, :description, :secret, :events)""",
        new MapSqlParameterSource().addValue("id", id).addValue("org", me.organizationId()).addValue("url", url)
            .addValue("description", blankToNull(req.description())).addValue("secret", secret).addValue("events", events));
    audit.log(me.organizationId(), me.userId(), "WEBHOOK_CREATED", AuditService.ORGANIZATION, me.organizationId(),
        Map.of("url", url, "events", events));
    return new CreatedEndpointDto(get(me.organizationId(), id), secret);
  }

  @Transactional
  public EndpointDto update(AuthPrincipal me, UUID id, UpdateEndpointRequest req) {
    EndpointDto current = get(me.organizationId(), id);
    Map<String, Object> changes = new LinkedHashMap<>();
    String url = current.url();
    if (req.url() != null && !req.url().trim().equals(current.url())) {
      url = UrlGuard.check(req.url(), props.allowPrivateTargets()).toString();
      changes.put("url", url);
    }
    String events = req.events() == null ? String.join(",", current.events()) : events(req.events());
    if (req.events() != null) changes.put("events", events);
    boolean enabled = req.enabled() == null ? current.enabled() : req.enabled();
    if (enabled != current.enabled()) changes.put("enabled", enabled);
    String description = req.description() == null ? current.description() : blankToNull(req.description());
    jdbc.update("""
        update webhook_endpoints set url = :url, events = :events, enabled = :enabled, description = :description
        where id = :id and organization_id = :org""",
        new MapSqlParameterSource().addValue("url", url).addValue("events", events).addValue("enabled", enabled)
            .addValue("description", description).addValue("id", id).addValue("org", me.organizationId()));
    if (!changes.isEmpty()) {
      audit.log(me.organizationId(), me.userId(), "WEBHOOK_UPDATED", AuditService.ORGANIZATION, me.organizationId(), changes);
    }
    return get(me.organizationId(), id);
  }

  @Transactional
  public void delete(AuthPrincipal me, UUID id) {
    EndpointDto current = get(me.organizationId(), id);
    jdbc.update("delete from webhook_endpoints where id = :id and organization_id = :org",
        new MapSqlParameterSource().addValue("id", id).addValue("org", me.organizationId()));
    audit.log(me.organizationId(), me.userId(), "WEBHOOK_DELETED", AuditService.ORGANIZATION, me.organizationId(),
        Map.of("url", current.url()));
  }

  /** Queues a {@code ping} for this endpoint only, so the admin can verify the URL and the signature handling. */
  @Transactional
  public DeliveryDto sendTest(AuthPrincipal me, UUID id) {
    get(me.organizationId(), id);
    UUID eventId = UUID.randomUUID();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", eventId);
    payload.put("type", "ping");
    payload.put("createdAt", Instant.now());
    payload.put("organizationId", me.organizationId());
    payload.put("data", Map.of("message", "This is a test event from InvoiceMind."));
    UUID deliveryId = insertDelivery(id, me.organizationId(), eventId, "ping", null, payload);
    return delivery(me.organizationId(), id, deliveryId);
  }

  @Transactional(readOnly = true)
  public List<DeliveryDto> deliveries(UUID org, UUID endpointId) {
    get(org, endpointId);
    return jdbc.query("""
        select * from webhook_deliveries where endpoint_id = :id and organization_id = :org
        order by created_at desc limit 50""",
        new MapSqlParameterSource().addValue("id", endpointId).addValue("org", org), (rs, i) -> toDelivery(rs));
  }

  @Transactional
  public DeliveryDto retry(AuthPrincipal me, UUID endpointId, UUID deliveryId) {
    delivery(me.organizationId(), endpointId, deliveryId);
    jdbc.update("""
        update webhook_deliveries set status = 'PENDING', attempts = 0, next_attempt_at = now(), last_error = null
        where id = :id and endpoint_id = :ep and organization_id = :org and status = 'FAILED'""",
        new MapSqlParameterSource().addValue("id", deliveryId).addValue("ep", endpointId).addValue("org", me.organizationId()));
    return delivery(me.organizationId(), endpointId, deliveryId);
  }

  // --------------------------------------------------------------- internals
  EndpointDto get(UUID org, UUID id) {
    return jdbc.query("select * from webhook_endpoints where id = :id and organization_id = :org",
            new MapSqlParameterSource().addValue("id", id).addValue("org", org), (rs, i) -> toDto(rs))
        .stream().findFirst().orElseThrow(() -> ApiException.notFound("Webhook"));
  }

  private DeliveryDto delivery(UUID org, UUID endpointId, UUID deliveryId) {
    return jdbc.query("""
            select * from webhook_deliveries where id = :id and endpoint_id = :ep and organization_id = :org""",
            new MapSqlParameterSource().addValue("id", deliveryId).addValue("ep", endpointId).addValue("org", org),
            (rs, i) -> toDelivery(rs))
        .stream().findFirst().orElseThrow(() -> ApiException.notFound("Delivery"));
  }

  UUID insertDelivery(UUID endpointId, UUID org, UUID eventId, String type, UUID invoiceId, Object payload) {
    UUID id = UUID.randomUUID();
    String json;
    try {
      json = mapper.writeValueAsString(payload);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Webhook payload is not serialisable", e);
    }
    jdbc.update("""
        insert into webhook_deliveries (id, endpoint_id, organization_id, event_id, event_type, invoice_id, payload, status)
        values (:id, :ep, :org, :event, :type, :invoice, cast(:payload as jsonb), 'PENDING')""",
        new MapSqlParameterSource().addValue("id", id).addValue("ep", endpointId).addValue("org", org)
            .addValue("event", eventId).addValue("type", type).addValue("invoice", invoiceId).addValue("payload", json));
    return id;
  }

  private static String events(List<String> requested) {
    List<String> distinct = requested.stream().map(String::trim).distinct().toList();
    if (distinct.isEmpty()) throw ApiException.badRequest("invalid_events", "Select at least one event");
    for (String e : distinct) {
      if (!WebhookDtos.EVENT_TYPES.contains(e)) {
        throw ApiException.badRequest("invalid_events", "Unknown event '" + e + "'; allowed: "
            + String.join(", ", WebhookDtos.EVENT_TYPES.stream().sorted().toList()));
      }
    }
    return String.join(",", distinct);
  }

  private static EndpointDto toDto(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new EndpointDto(UUID.fromString(rs.getString("id")), rs.getString("url"), rs.getString("description"),
        Arrays.asList(rs.getString("events").split(",")), rs.getBoolean("enabled"), rs.getTimestamp("created_at").toInstant());
  }

  private static DeliveryDto toDelivery(java.sql.ResultSet rs) throws java.sql.SQLException {
    int code = rs.getInt("last_status_code");
    Integer lastCode = rs.wasNull() ? null : code;
    String invoice = rs.getString("invoice_id");
    var delivered = rs.getTimestamp("delivered_at");
    return new DeliveryDto(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("event_id")),
        rs.getString("event_type"), invoice == null ? null : UUID.fromString(invoice), rs.getString("status"),
        rs.getInt("attempts"), lastCode, rs.getString("last_error"), rs.getTimestamp("created_at").toInstant(),
        delivered == null ? null : delivered.toInstant(),
        "PENDING".equals(rs.getString("status")) ? rs.getTimestamp("next_attempt_at").toInstant() : null);
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
