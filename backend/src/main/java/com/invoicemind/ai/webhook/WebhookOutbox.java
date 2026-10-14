package com.invoicemind.ai.webhook;

import com.invoicemind.ai.invoice.InvoiceEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox: called from inside the transaction that changes an invoice, it writes one pending delivery
 * per subscribed endpoint. Either both the change and its events commit or neither does; the dispatcher sends them.
 */
@Component
@RequiredArgsConstructor
public class WebhookOutbox {
  private final NamedParameterJdbcTemplate jdbc;
  private final WebhookService service;

  @Transactional
  public void invoiceEvent(String type, InvoiceEntity invoice) {
    List<UUID> endpoints = jdbc.query("""
            select id from webhook_endpoints
            where organization_id = :org and enabled and position(',' || :type || ',' in ',' || events || ',') > 0""",
        new MapSqlParameterSource().addValue("org", invoice.getOrganizationId()).addValue("type", type),
        (rs, i) -> UUID.fromString(rs.getString("id")));
    if (endpoints.isEmpty()) return;

    UUID eventId = UUID.randomUUID();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", eventId);
    payload.put("type", type);
    payload.put("createdAt", Instant.now());
    payload.put("organizationId", invoice.getOrganizationId());
    payload.put("data", Map.of("invoice", describe(invoice)));
    for (UUID endpoint : endpoints) {
      service.insertDelivery(endpoint, invoice.getOrganizationId(), eventId, type, invoice.getId(), payload);
    }
  }

  /** The invoice as an integration sees it: the business data and the validation outcome, nothing internal. */
  static Map<String, Object> describe(InvoiceEntity i) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", i.getId());
    m.put("status", i.getStatus());
    m.put("documentType", i.getDocumentType());
    m.put("vendor", i.getVendorName());
    m.put("invoiceNumber", i.getInvoiceNumber());
    m.put("invoiceDate", i.getInvoiceDate());
    m.put("dueDate", i.getDueDate());
    m.put("currency", i.getCurrency());
    m.put("subtotal", i.getSubtotal());
    m.put("tax", i.getTax());
    m.put("total", i.getTotal());
    m.put("lineItems", i.getLineItems().stream().map(l -> {
      Map<String, Object> line = new LinkedHashMap<>();
      line.put("description", l.getDescription());
      line.put("quantity", l.getQuantity());
      line.put("unitPrice", l.getUnitPrice());
      line.put("total", l.getTotal());
      return line;
    }).toList());
    m.put("issues", i.getIssues().stream().map(x -> {
      Map<String, Object> issue = new LinkedHashMap<>();
      issue.put("code", x.getCode());
      issue.put("severity", x.getSeverity());
      issue.put("field", x.getField());
      issue.put("message", x.getMessage());
      return issue;
    }).toList());
    m.put("humanReviewed", i.isHumanReviewed());
    m.put("failureReason", i.getFailureReason());
    m.put("decidedAt", i.getDecidedAt());
    m.put("decisionComment", i.getDecisionComment());
    m.put("createdAt", i.getCreatedAt());
    return m;
  }
}
