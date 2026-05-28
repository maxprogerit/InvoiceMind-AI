package com.invoicemind.ai.service;

import com.invoicemind.ai.audit.AuditLogService;
import com.invoicemind.ai.entity.VendorEntity;
import com.invoicemind.ai.repository.InvoiceRepository;
import com.invoicemind.ai.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VendorService {
  private final VendorRepository vendorRepository;
  private final InvoiceRepository invoiceRepository;
  private final AuditLogService auditLogService;

  public List<VendorEntity> listAll() {
    return vendorRepository.findByOrganizationIdOrderByNameAsc(1L);
  }

  public VendorEntity getById(Long id) {
    return vendorRepository.findById(id).orElseThrow(() -> new RuntimeException("Vendor not found"));
  }

  public VendorEntity create(VendorEntity vendor) {
    vendor.setOrganizationId(1L);
    if (vendor.getRiskScore() == null) vendor.setRiskScore(BigDecimal.valueOf(0.1));
    VendorEntity saved = vendorRepository.save(vendor);
    auditLogService.log(1L, "VENDOR_CREATED", "vendor", saved.getId(),
        "{\"name\":\"" + saved.getName() + "\"}");
    return saved;
  }

  public VendorEntity update(Long id, VendorEntity updated) {
    VendorEntity existing = vendorRepository.findById(id).orElseThrow();
    if (updated.getName() != null) existing.setName(updated.getName());
    if (updated.getContactEmail() != null) existing.setContactEmail(updated.getContactEmail());
    if (updated.getPaymentTerms() != null) existing.setPaymentTerms(updated.getPaymentTerms());
    if (updated.getRiskScore() != null) existing.setRiskScore(updated.getRiskScore());
    if (updated.getCountry() != null) existing.setCountry(updated.getCountry());
    if (updated.getContactPhone() != null) existing.setContactPhone(updated.getContactPhone());
    if (updated.getAddress() != null) existing.setAddress(updated.getAddress());
    return vendorRepository.save(existing);
  }

  public void delete(Long id) {
    vendorRepository.findById(id).orElseThrow(() -> new RuntimeException("Vendor not found"));
    vendorRepository.deleteById(id);
    auditLogService.log(1L, "VENDOR_DELETED", "vendor", id, "{}");
  }

  public Map<String, Object> getStats(Long vendorId) {
    BigDecimal totalSpend = invoiceRepository.findByOrganizationIdAndVendorIdOrderByIdDesc(1L, vendorId)
        .stream().map(i -> i.getTotalAmount() != null ? i.getTotalAmount() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    long invoiceCount = invoiceRepository.findByOrganizationIdAndVendorIdOrderByIdDesc(1L, vendorId).size();
    return Map.of("totalSpend", totalSpend, "invoiceCount", invoiceCount);
  }
}
