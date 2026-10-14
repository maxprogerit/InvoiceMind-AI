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

  /** Atomically claims an UPLOADED invoice; returns 0 if another worker already took it (idempotent consumer). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update InvoiceEntity i set i.status = com.invoicemind.ai.invoice.InvoiceStatus.PROCESSING,
        i.processingStartedAt = :now, i.failureReason = null, i.updatedAt = :now, i.version = i.version + 1
      where i.id = :id and i.status = com.invoicemind.ai.invoice.InvoiceStatus.UPLOADED""")
  int claimForProcessing(@Param("id") UUID id, @Param("now") Instant now);

  @Query("""
      select i.id from InvoiceEntity i
      where i.organizationId = :org and i.id <> :self and i.vendorId = :vendorId
        and lower(i.invoiceNumber) = lower(:number)
        and i.status in :statuses and i.createdAt < :createdBefore
      order by i.createdAt asc""")
  List<UUID> findDuplicateIds(@Param("org") UUID org, @Param("self") UUID self, @Param("vendorId") UUID vendorId,
                              @Param("number") String number,
                              @Param("statuses") Collection<InvoiceStatus> statuses,
                              @Param("createdBefore") Instant createdBefore);

  boolean existsByOrganizationIdAndFileSha256AndIdNotAndStatusNot(UUID organizationId, String sha256, UUID id,
                                                                  InvoiceStatus status);

  List<InvoiceEntity> findTop50ByStatusAndProcessingStartedAtBefore(InvoiceStatus status, Instant before);

  List<InvoiceEntity> findTop50ByStatusAndUpdatedAtBefore(InvoiceStatus status, Instant before);
}
