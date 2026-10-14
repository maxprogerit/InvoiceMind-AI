package com.invoicemind.ai.invoice;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Query filters shared by the list endpoint and the exports. The tenant/scope restriction is always applied first. */
public record InvoiceFilter(
    List<InvoiceStatus> statuses,
    String q,
    String vendor,
    String currency,
    LocalDate from,
    LocalDate to,
    BigDecimal minTotal,
    BigDecimal maxTotal) {

  public static InvoiceFilter none() {
    return new InvoiceFilter(null, null, null, null, null, null, null, null);
  }

  /**
   * @param uploaderScope when non-null the caller may only see invoices uploaded by that user (role USER)
   */
  public Specification<InvoiceEntity> toSpec(UUID organizationId, UUID uploaderScope) {
    return (root, query, cb) -> {
      List<Predicate> p = new ArrayList<>();
      p.add(cb.equal(root.get("organizationId"), organizationId));
      if (uploaderScope != null) p.add(cb.equal(root.get("uploadedBy"), uploaderScope));
      if (statuses != null && !statuses.isEmpty()) p.add(root.get("status").in(statuses));
      if (hasText(q)) {
        String like = "%" + escape(q.trim().toLowerCase(Locale.ROOT)) + "%";
        p.add(cb.or(
            cb.like(cb.lower(root.get("invoiceNumber")), like, '\\'),
            cb.like(cb.lower(root.get("vendorName")), like, '\\'),
            cb.like(cb.lower(root.get("originalFilename")), like, '\\')));
      }
      if (hasText(vendor)) {
        p.add(cb.like(cb.lower(root.get("vendorName")), "%" + escape(vendor.trim().toLowerCase(Locale.ROOT)) + "%", '\\'));
      }
      if (hasText(currency)) p.add(cb.equal(root.get("currency"), currency.trim().toUpperCase(Locale.ROOT)));
      if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), from));
      if (to != null) p.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), to));
      if (minTotal != null) p.add(cb.greaterThanOrEqualTo(root.get("total"), minTotal));
      if (maxTotal != null) p.add(cb.lessThanOrEqualTo(root.get("total"), maxTotal));
      return cb.and(p.toArray(Predicate[]::new));
    };
  }

  private static boolean hasText(String s) { return s != null && !s.isBlank(); }

  /** Neutralise LIKE wildcards typed by the user. */
  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
