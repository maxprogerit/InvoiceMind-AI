package com.invoicemind.ai.invoice;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID>, JpaSpecificationExecutor<InvoiceEntity> {

  Optional<InvoiceEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

  long countByVendorId(UUID vendorId);

  List<InvoiceEntity> findByOrganizationIdAndVendorIdAndStatusIn(UUID organizationId, UUID vendorId,
                                                                 Collection<InvoiceStatus> statuses);

  List<InvoiceEntity> findByOrganizationIdAndVendorIdAndStatusInAndCreatedAtAfterOrderByCreatedAtAsc(
      UUID organizationId, UUID vendorId, Collection<InvoiceStatus> statuses, Instant createdAfter);

  @Query("""
      select i.vendorId, count(i) from InvoiceEntity i
      where i.organizationId = :org and i.vendorId is not null group by i.vendorId""")
  List<Object[]> countByVendor(@Param("org") UUID organizationId);

  /** Atomically claims an UPLOADED invoice; returns 0 if another worker already took it (idempotent consumer). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update InvoiceEntity i set i.status = com.invoicemind.ai.invoice.InvoiceStatus.PROCESSING,
        i.processingStartedAt = :now, i.failureReason = null, i.updatedAt = :now, i.version = i.version + 1
      where i.id = :id and i.status = com.invoicemind.ai.invoice.InvoiceStatus.UPLOADED""")
  int claimForProcessing(@Param("id") UUID id, @Param("now") Instant now);

  @Query("""
      select i.id from InvoiceEntity i
      where i.organizationId = :org and i.id <> :self and i.documentType = :type and i.vendorId = :vendorId
        and lower(i.invoiceNumber) = lower(:number)
        and i.status in :statuses and i.createdAt < :createdBefore
      order by i.createdAt asc""")
  List<UUID> findDuplicateIds(@Param("org") UUID org, @Param("self") UUID self, @Param("type") DocumentType type,
                              @Param("vendorId") UUID vendorId,
                              @Param("number") String number,
                              @Param("statuses") Collection<InvoiceStatus> statuses,
                              @Param("createdBefore") Instant createdBefore);

  /** Older invoices of the organisation with the byte-identical file, oldest first. */
  @Query("""
      select i from InvoiceEntity i
      where i.organizationId = :org and i.id <> :self and i.fileSha256 = :sha
        and i.status in :statuses and i.createdAt < :createdBefore
      order by i.createdAt asc""")
  List<InvoiceEntity> findEarlierSameFile(@Param("org") UUID org, @Param("self") UUID self, @Param("sha") String sha256,
                                          @Param("statuses") Collection<InvoiceStatus> statuses,
                                          @Param("createdBefore") Instant createdBefore);

  /** Same vendor, same total and currency, invoice date in [from, to]: candidates for a possible duplicate. */
  @Query("""
      select i from InvoiceEntity i
      where i.organizationId = :org and i.id <> :self and i.documentType = :type and i.vendorId = :vendorId
        and i.total = :total and (i.currency = :currency or (i.currency is null and :currency is null))
        and i.invoiceDate between :from and :to
        and i.status in :statuses and i.createdAt < :createdBefore
      order by i.createdAt asc""")
  List<InvoiceEntity> findPossibleDuplicates(@Param("org") UUID org, @Param("self") UUID self,
                                             @Param("type") DocumentType type, @Param("vendorId") UUID vendorId,
                                             @Param("total") java.math.BigDecimal total,
                                             @Param("currency") String currency,
                                             @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to,
                                             @Param("statuses") Collection<InvoiceStatus> statuses,
                                             @Param("createdBefore") Instant createdBefore);
}
