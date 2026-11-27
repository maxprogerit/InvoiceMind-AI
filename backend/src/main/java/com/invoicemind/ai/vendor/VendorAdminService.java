package com.invoicemind.ai.vendor;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.invoice.InvoiceEntity;
import com.invoicemind.ai.invoice.InvoiceRepository;
import com.invoicemind.ai.invoice.InvoiceStatus;
import com.invoicemind.ai.processing.StatusPublisher;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.user.UserRepository;
import com.invoicemind.ai.validation.ValidationService;
import com.invoicemind.ai.vendor.VendorDtos.AliasDto;
import com.invoicemind.ai.vendor.VendorDtos.UpdateVendorRequest;
import com.invoicemind.ai.vendor.VendorDtos.VendorDto;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vendor master data: list, confirm, correct, aliases. Every change is audited and re-checks open invoices. */
@Service
@RequiredArgsConstructor
public class VendorAdminService {
  private final VendorRepository vendors;
  private final VendorAliasRepository aliases;
  private final InvoiceRepository invoices;
  private final UserRepository users;
  private final ValidationService validation;
  private final StatusPublisher status;
  private final AuditService audit;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<VendorDto> list(AuthPrincipal me, VendorStatus filter) {
    UUID org = me.organizationId();
    Map<UUID, List<AliasDto>> byVendor = aliases.findByOrganizationIdOrderByAliasAsc(org).stream()
        .collect(Collectors.groupingBy(VendorAliasEntity::getVendorId,
            Collectors.mapping(a -> new AliasDto(a.getId(), a.getAlias()), Collectors.toList())));
    Map<UUID, Long> counts = new HashMap<>();
    for (Object[] row : invoices.countByVendor(org)) counts.put((UUID) row[0], (Long) row[1]);
    Map<UUID, String> names = users.findAllById(vendors.findByOrganizationIdOrderByNameAsc(org).stream()
            .map(VendorEntity::getVerifiedBy).filter(Objects::nonNull).distinct().toList()).stream()
        .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName));
    return vendors.findByOrganizationIdOrderByNameAsc(org).stream()
        .filter(v -> filter == null || v.getStatus() == filter)
        .map(v -> toDto(v, byVendor.getOrDefault(v.getId(), List.of()), counts.getOrDefault(v.getId(), 0L),
            v.getVerifiedBy() == null ? null : names.get(v.getVerifiedBy())))
        .toList();
  }

  @Transactional
  public VendorDto verify(UUID id, AuthPrincipal me) {
    VendorEntity v = load(id, me);
    if (!v.isVerified()) {
      v.setStatus(VendorStatus.VERIFIED);
      v.setVerifiedBy(me.userId());
      v.setVerifiedAt(Instant.now(clock));
      vendors.saveAndFlush(v);
      audit.log(me.organizationId(), me.userId(), "VENDOR_VERIFIED", AuditService.VENDOR, v.getId(),
          Map.of("name", v.getName(), "taxId", String.valueOf(v.getTaxId())));
      recheckOpenInvoices(v);
    }
    return one(v, me);
  }

  @Transactional
  public VendorDto update(UUID id, UpdateVendorRequest req, AuthPrincipal me) {
    VendorEntity v = load(id, me);
    String taxId = req.taxId() == null || req.taxId().isBlank() ? null : VendorEntity.normalizeTaxId(req.taxId());
    if (req.taxId() != null && !req.taxId().isBlank() && taxId == null) {
      throw ApiException.badRequest("invalid_tax_id", "The tax id must have at least 5 letters or digits");
    }
    if (taxId != null) {
      vendors.findByOrganizationIdAndTaxId(me.organizationId(), taxId).filter(other -> !other.getId().equals(v.getId()))
          .ifPresent(other -> { throw ApiException.conflict("tax_id_taken", "Vendor '" + other.getName() + "' already has this tax id"); });
    }
    String currency = req.defaultCurrency() == null || req.defaultCurrency().isBlank() ? null
        : req.defaultCurrency().toUpperCase(Locale.ROOT);
    Map<String, Object> changes = new LinkedHashMap<>();
    if (!req.name().trim().equals(v.getName())) changes.put("name", v.getName() + " -> " + req.name().trim());
    if (!Objects.equals(taxId, v.getTaxId())) changes.put("taxId", v.getTaxId() + " -> " + taxId);
    if (!Objects.equals(currency, v.getDefaultCurrency())) changes.put("defaultCurrency", v.getDefaultCurrency() + " -> " + currency);
    v.rename(req.name());
    v.setTaxId(taxId);
    v.setDefaultCurrency(currency);
    vendors.saveAndFlush(v);
    if (!changes.isEmpty()) {
      audit.log(me.organizationId(), me.userId(), "VENDOR_UPDATED", AuditService.VENDOR, v.getId(), changes);
    }
    return one(v, me);
  }

  @Transactional
  public VendorDto addAlias(UUID id, String alias, AuthPrincipal me) {
    VendorEntity v = load(id, me);
    String normalized = VendorEntity.normalize(alias);
    if (normalized.isEmpty()) throw ApiException.badRequest("invalid_alias", "The alias has no letters or digits");
    aliases.findByOrganizationIdAndNormalizedAlias(me.organizationId(), normalized).ifPresent(a -> {
      throw ApiException.conflict("alias_taken", "This alias already belongs to a vendor");
    });
    boolean nameOfAnother = vendors.findByNameOrAlias(me.organizationId(), normalized).stream()
        .anyMatch(other -> !other.getId().equals(v.getId()));
    if (nameOfAnother) throw ApiException.conflict("alias_taken", "Another vendor already has this name");
    aliases.saveAndFlush(new VendorAliasEntity(v.getId(), me.organizationId(), alias, me.userId()));
    audit.log(me.organizationId(), me.userId(), "VENDOR_ALIAS_ADDED", AuditService.VENDOR, v.getId(), Map.of("alias", alias.trim()));
    return one(v, me);
  }

  @Transactional
  public VendorDto removeAlias(UUID id, long aliasId, AuthPrincipal me) {
    VendorEntity v = load(id, me);
    VendorAliasEntity a = aliases.findByIdAndVendorId(aliasId, v.getId()).orElseThrow(() -> ApiException.notFound("Alias"));
    aliases.delete(a);
    audit.log(me.organizationId(), me.userId(), "VENDOR_ALIAS_REMOVED", AuditService.VENDOR, v.getId(), Map.of("alias", a.getAlias()));
    return one(v, me);
  }

  /** Confirming a vendor removes the "unverified vendor" finding from its open invoices at once. */
  private void recheckOpenInvoices(VendorEntity v) {
    for (InvoiceEntity inv : invoices.findByOrganizationIdAndVendorIdAndStatusIn(v.getOrganizationId(), v.getId(),
        InvoiceStatus.AWAITING_DECISION)) {
      inv.setStatus(validation.revalidate(inv));
      invoices.saveAndFlush(inv);
      status.publish(inv);
    }
  }

  private VendorEntity load(UUID id, AuthPrincipal me) {
    return vendors.findByIdAndOrganizationId(id, me.organizationId()).orElseThrow(() -> ApiException.notFound("Vendor"));
  }

  private VendorDto one(VendorEntity v, AuthPrincipal me) {
    List<AliasDto> list = aliases.findByVendorIdOrderByAliasAsc(v.getId()).stream()
        .map(a -> new AliasDto(a.getId(), a.getAlias())).toList();
    long count = invoices.countByVendor(me.organizationId()).stream().filter(r -> v.getId().equals(r[0]))
        .mapToLong(r -> (Long) r[1]).sum();
    String verifiedBy = v.getVerifiedBy() == null ? null : users.findById(v.getVerifiedBy()).map(UserEntity::getFullName).orElse(null);
    return toDto(v, list, count, verifiedBy);
  }

  private static VendorDto toDto(VendorEntity v, List<AliasDto> aliases, long count, String verifiedBy) {
    return new VendorDto(v.getId(), v.getName(), v.getTaxId(), v.getDefaultCurrency(), v.getStatus(), verifiedBy,
        v.getVerifiedAt(), aliases, count, v.getCreatedAt());
  }
}
