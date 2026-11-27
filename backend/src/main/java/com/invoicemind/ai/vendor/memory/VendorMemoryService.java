package com.invoicemind.ai.vendor.memory;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.invoice.InvoiceEntity;
import com.invoicemind.ai.invoice.InvoiceRepository;
import com.invoicemind.ai.vendor.VendorAliasEntity;
import com.invoicemind.ai.vendor.VendorAliasRepository;
import com.invoicemind.ai.vendor.VendorEntity;
import com.invoicemind.ai.vendor.VendorRepository;
import com.invoicemind.ai.vendor.VendorService.MatchedBy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vendor memory: plain, inspectable rules learned from people's corrections, applied to the next invoice of the
 * same vendor. This is not model training: nothing is sent to the model, the rules are rows a person can read and
 * delete (Vendors page), and every value they fill in is marked on the invoice as "from vendor memory".
 *
 * <ul>
 *   <li>currency: "when this vendor's document reads X (or nothing), it is Y";</li>
 *   <li>payment terms: due date = invoice date + N days when the document prints no due date;</li>
 *   <li>expense category: pre-filled when none is set;</li>
 *   <li>vendor name: a misread name corrected to an existing vendor becomes that vendor's alias.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class VendorMemoryService {
  /** One value filled in on an invoice; stored on it and shown in the UI. */
  public record Applied(String field, String value, String extracted, Long memoryId) {
    Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("field", field);
      m.put("value", value);
      m.put("extracted", extracted);
      m.put("memoryId", memoryId);
      return m;
    }
  }

  /** What an invoice looked like before a person corrected it. */
  public record Before(UUID vendorId, String vendorName, String currency, LocalDate dueDate, String category) {
    public static Before of(InvoiceEntity i) {
      return new Before(i.getVendorId(), i.getVendorName(), i.getCurrency(), i.getDueDate(), i.getExpenseCategory());
    }
  }

  private final VendorMemoryRepository memory;
  private final VendorRepository vendors;
  private final VendorAliasRepository aliases;
  private final InvoiceRepository invoices;
  private final AuditService audit;
  private final Clock clock;

  // ------------------------------------------------------------------- apply
  /** Fills in what the vendor's memory knows. Joins the processing transaction; mutates the invoice. */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<Applied> apply(InvoiceEntity inv, MatchedBy matchedBy, String extractedVendorName) {
    List<Applied> applied = new ArrayList<>();
    if (inv.getVendorId() == null) return applied;
    VendorEntity vendor = vendors.findById(inv.getVendorId()).orElse(null);
    if (vendor == null) return applied;

    if (matchedBy == MatchedBy.ALIAS) applied.add(new Applied("vendor", vendor.getName(), extractedVendorName, null));

    String extractedCurrency = inv.getCurrency();
    memory.find(vendor.getId(), MemoryField.CURRENCY, extractedCurrency)
        .filter(m -> !m.getValue().equals(extractedCurrency))
        .ifPresent(m -> {
          inv.setCurrency(m.getValue());
          applied.add(use(m, "currency", extractedCurrency));
        });

    // due date: printed on the invoice, else invoice date + the vendor's payment terms (set by a person on the
    // Vendors page or learned from a correction)
    if (inv.getDueDate() == null && inv.getInvoiceDate() != null && vendor.getPaymentTermsDays() != null) {
      inv.setDueDate(inv.getInvoiceDate().plusDays(vendor.getPaymentTermsDays()));
      applied.add(memory.find(vendor.getId(), MemoryField.PAYMENT_TERMS, null)
          .map(m -> use(m, "dueDate", null))
          .orElse(new Applied("dueDate", vendor.getPaymentTermsDays() + " days", null, null)));
    }

    if (inv.getExpenseCategory() == null) {
      memory.find(vendor.getId(), MemoryField.CATEGORY, null).ifPresent(m -> {
        inv.setExpenseCategory(m.getValue());
        applied.add(use(m, "expenseCategory", null));
      });
    }

    inv.setMemoryApplied(applied.isEmpty() ? null : applied.stream().map(Applied::toMap).toList());
    if (!applied.isEmpty()) {
      audit.log(inv.getOrganizationId(), null, "VENDOR_MEMORY_APPLIED", AuditService.INVOICE, inv.getId(),
          Map.of("vendor", vendor.getName(), "fields", applied.stream().map(Applied::field).toList()));
    }
    return applied;
  }

  private Applied use(VendorMemoryEntity m, String field, String extracted) {
    m.setTimesApplied(m.getTimesApplied() + 1);
    memory.save(m);
    String shown = m.getField() == MemoryField.PAYMENT_TERMS ? m.getValue() + " days" : m.getValue();
    return new Applied(field, shown, extracted, m.getId());
  }

  // ------------------------------------------------------------------- learn
  /** Learns from a person's correction of {@code inv} (already updated; {@code before} is the previous state). */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<String> learn(InvoiceEntity inv, Before before, UUID userId) {
    List<String> learned = new ArrayList<>();
    if (inv.getVendorId() == null) return learned;
    VendorEntity vendor = vendors.findById(inv.getVendorId()).orElse(null);
    if (vendor == null) return learned;

    learnAlias(inv, before, vendor, userId).ifPresent(learned::add);

    if (!Objects.equals(before.currency(), inv.getCurrency()) && inv.getCurrency() != null) {
      String extracted = extractedCurrency(inv);
      if (inv.getCurrency().equals(extracted)) {
        // the person went back to what the document says: forget the rule that changed it
        memory.find(vendor.getId(), MemoryField.CURRENCY, extracted).ifPresent(memory::delete);
      } else {
        upsert(vendor, MemoryField.CURRENCY, extracted, inv.getCurrency(), inv, userId);
        learned.add("currency");
      }
      vendor.setDefaultCurrency(inv.getCurrency());
    }

    if (!Objects.equals(before.dueDate(), inv.getDueDate()) && inv.getDueDate() != null && inv.getInvoiceDate() != null) {
      long days = ChronoUnit.DAYS.between(inv.getInvoiceDate(), inv.getDueDate());
      if (days >= 0 && days <= 365) {
        upsert(vendor, MemoryField.PAYMENT_TERMS, null, Long.toString(days), inv, userId);
        vendor.setPaymentTermsDays((int) days);
        learned.add("paymentTerms");
      }
    }

    if (!Objects.equals(before.category(), inv.getExpenseCategory()) && inv.getExpenseCategory() != null) {
      upsert(vendor, MemoryField.CATEGORY, null, inv.getExpenseCategory(), inv, userId);
      vendor.setDefaultCategory(inv.getExpenseCategory());
      learned.add("category");
    }
    vendors.save(vendor);
    if (!learned.isEmpty()) {
      audit.log(inv.getOrganizationId(), userId, "VENDOR_MEMORY_LEARNED", AuditService.VENDOR, vendor.getId(),
          Map.of("invoice", inv.getId().toString(), "fields", learned));
    }
    return learned;
  }

  /**
   * A person corrected the vendor name and the invoice now belongs to another, existing vendor: the misread name
   * becomes that vendor's alias, and the vendor that was created from the misread name is removed if nothing uses it.
   */
  private Optional<String> learnAlias(InvoiceEntity inv, Before before, VendorEntity vendor, UUID userId) {
    if (before.vendorId() == null || before.vendorId().equals(vendor.getId()) || before.vendorName() == null) {
      return Optional.empty();
    }
    String normalized = VendorEntity.normalize(before.vendorName());
    if (normalized.isEmpty() || normalized.equals(vendor.getNormalizedName())) return Optional.empty();

    vendors.findById(before.vendorId())
        .filter(old -> !old.isVerified() && old.getNormalizedName().equals(normalized))
        .filter(old -> invoices.countByVendorId(old.getId()) == 0)
        .ifPresent(old -> {
          vendors.delete(old);
          vendors.flush();
          audit.log(inv.getOrganizationId(), userId, "VENDOR_REMOVED_UNUSED", AuditService.VENDOR, old.getId(),
              Map.of("name", old.getName(), "reason", "created from a misread name, now an alias of " + vendor.getName()));
        });
    boolean taken = aliases.findByOrganizationIdAndNormalizedAlias(inv.getOrganizationId(), normalized).isPresent()
        || vendors.findByNameOrAlias(inv.getOrganizationId(), normalized).stream().anyMatch(v -> !v.getId().equals(vendor.getId()));
    if (taken) return Optional.empty();
    aliases.save(new VendorAliasEntity(vendor.getId(), inv.getOrganizationId(), before.vendorName(), userId));
    return Optional.of("alias");
  }

  private void upsert(VendorEntity vendor, MemoryField field, String whenExtracted, String value, InvoiceEntity inv,
                      UUID userId) {
    VendorMemoryEntity m = memory.find(vendor.getId(), field, whenExtracted).orElseGet(VendorMemoryEntity::new);
    m.setVendorId(vendor.getId());
    m.setOrganizationId(vendor.getOrganizationId());
    m.setField(field);
    m.setWhenExtracted(whenExtracted);
    m.setValue(value);
    m.setLearnedFromInvoice(inv.getId());
    m.setLearnedBy(userId);
    m.setLearnedAt(Instant.now(clock));
    memory.save(m);
  }

  /** The currency exactly as the model read it (before any memory was applied). */
  private static String extractedCurrency(InvoiceEntity inv) {
    Object raw = inv.getExtractionJson() == null ? null : inv.getExtractionJson().get("currency");
    if (!(raw instanceof String s) || s.isBlank()) return null;
    return s.trim().toUpperCase(Locale.ROOT);
  }
}
