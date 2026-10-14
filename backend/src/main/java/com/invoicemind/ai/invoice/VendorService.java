package com.invoicemind.ai.invoice;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VendorService {
  private final VendorRepository vendors;

  /** Joins the caller's transaction. Returns null when the name has no usable characters. */
  public UUID findOrCreate(UUID organizationId, String name) {
    if (name == null) return null;
    String normalized = VendorEntity.normalize(name);
    if (normalized.isEmpty()) return null;
    return vendors.findByOrganizationIdAndNormalizedName(organizationId, normalized)
        .orElseGet(() -> vendors.saveAndFlush(new VendorEntity(organizationId, name))).getId();
  }
}
