package com.invoicemind.ai.vendor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the vendor an invoice belongs to. Deterministic, in this order:
 * <ol>
 *   <li>tax / VAT id: exact match on the normalised id;</li>
 *   <li>name: the normalised name equals a vendor's name or one of its aliases, and that vendor's tax id is
 *       either unknown or the same (a different tax id means a different company with the same name);</li>
 *   <li>otherwise a new vendor is created as {@link VendorStatus#UNVERIFIED}, for a person to confirm.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class VendorService {
  public enum MatchedBy { TAX_ID, NAME, CREATED }

  public record Match(UUID vendorId, MatchedBy matchedBy) {}

  private final VendorRepository vendors;
  private final NamedParameterJdbcTemplate jdbc;

  /**
   * Joins the caller's transaction (which it requires). Returns null when there is neither a usable name nor a tax id.
   * Two documents of a new vendor processed at the same moment must end up with one vendor, not two, or the duplicate
   * rules (which compare vendors) would miss them: lookup and creation are serialised per organisation and name / tax id
   * with transaction-scoped advisory locks, released at commit.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Match match(UUID organizationId, String name, String rawTaxId, String currency) {
    String taxId = VendorEntity.normalizeTaxId(rawTaxId);
    String normalized = name == null ? "" : VendorEntity.normalize(name);
    if (!normalized.isEmpty()) lock(organizationId + "|name|" + normalized);
    if (taxId != null) lock(organizationId + "|tax|" + taxId); // always after the name lock: one order, no deadlock

    if (taxId != null) {
      Optional<VendorEntity> byTax = vendors.findByOrganizationIdAndTaxId(organizationId, taxId);
      if (byTax.isPresent()) return new Match(byTax.get().getId(), MatchedBy.TAX_ID);
    }
    if (!normalized.isEmpty()) {
      List<VendorEntity> byName = vendors.findByNameOrAlias(organizationId, normalized);
      for (VendorEntity v : byName) {
        if (v.getTaxId() == null || v.getTaxId().equals(taxId) || taxId == null) {
          // learn the tax id only while the vendor is still unconfirmed model output; confirmed data is changed by people
          if (taxId != null && v.getTaxId() == null && !v.isVerified()) {
            v.setTaxId(taxId);
            vendors.saveAndFlush(v);
          }
          return new Match(v.getId(), MatchedBy.NAME);
        }
      }
    }
    if (normalized.isEmpty()) {
      if (taxId == null) return null;
      normalized = taxId.toLowerCase();
      name = taxId;
    }
    VendorEntity created = new VendorEntity(organizationId, name);
    created.setTaxId(taxId);
    created.setDefaultCurrency(currency != null && currency.matches("[A-Z]{3}") ? currency : null);
    return new Match(vendors.saveAndFlush(created).getId(), MatchedBy.CREATED);
  }

  private void lock(String key) {
    jdbc.queryForObject("select 1 from (select pg_advisory_xact_lock(hashtextextended(:k, 0))) l",
        new MapSqlParameterSource("k", key), Integer.class);
  }

  public Optional<VendorEntity> find(UUID vendorId) {
    return vendorId == null ? Optional.empty() : vendors.findById(vendorId);
  }
}
