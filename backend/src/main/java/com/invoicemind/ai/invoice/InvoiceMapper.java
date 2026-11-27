package com.invoicemind.ai.invoice;

import com.invoicemind.ai.invoice.InvoiceDtos.DuplicateInfo;
import com.invoicemind.ai.invoice.InvoiceDtos.ExtractCheck;
import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceDetail;
import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceSummary;
import com.invoicemind.ai.invoice.InvoiceDtos.IssueDto;
import com.invoicemind.ai.invoice.InvoiceDtos.LineItemDto;
import com.invoicemind.ai.invoice.InvoiceDtos.OcrInfo;
import com.invoicemind.ai.invoice.InvoiceDtos.Permissions;
import com.invoicemind.ai.invoice.InvoiceDtos.UsageInfo;
import com.invoicemind.ai.invoice.InvoiceDtos.VendorRef;
import com.invoicemind.ai.vendor.VendorEntity;
import com.invoicemind.ai.vendor.VendorRepository;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.user.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Entity -> DTO mapping. Call inside a transaction: it touches lazy collections. */
@Component
@RequiredArgsConstructor
public class InvoiceMapper {
  private final UserRepository users;
  private final VendorRepository vendors;
  private final InvoiceRepository invoices;

  public List<InvoiceSummary> toSummaries(Collection<InvoiceEntity> invoices) {
    Map<UUID, String> names = names(invoices.stream().map(InvoiceEntity::getUploadedBy).toList());
    Set<UUID> verified = verifiedVendors(invoices.stream().map(InvoiceEntity::getVendorId).filter(Objects::nonNull).toList());
    return invoices.stream().map(i -> toSummary(i, names.getOrDefault(i.getUploadedBy(), "Unknown"),
        i.getVendorId() == null || verified.contains(i.getVendorId()))).toList();
  }

  public InvoiceSummary toSummary(InvoiceEntity i, String uploaderName) {
    return toSummary(i, uploaderName, isVendorVerified(i));
  }

  private InvoiceSummary toSummary(InvoiceEntity i, String uploaderName, boolean vendorVerified) {
    int errors = (int) i.getIssues().stream().filter(x -> "ERROR".equals(x.getSeverity())).count();
    return new InvoiceSummary(i.getId(), i.getStatus(), i.getDocumentType(), i.getOriginalFilename(), i.getVendorName(),
        i.getInvoiceNumber(), i.getInvoiceDate(), i.getDueDate(), i.getCurrency(), i.getTotal(),
        i.getUploadedBy(), uploaderName, errors, i.getIssues().size() - errors, i.getCreatedAt(),
        i.getDuplicateStatus(), vendorVerified);
  }

  public boolean isVendorVerified(InvoiceEntity i) {
    return i.getVendorId() == null || vendors.findById(i.getVendorId()).map(VendorEntity::isVerified).orElse(false);
  }

  private Set<UUID> verifiedVendors(Collection<UUID> ids) {
    if (ids.isEmpty()) return Set.of();
    return vendors.findAllById(new java.util.HashSet<>(ids)).stream().filter(VendorEntity::isVerified)
        .map(VendorEntity::getId).collect(Collectors.toSet());
  }

  public InvoiceDetail toDetail(InvoiceEntity i, AuthPrincipal viewer) {
    Map<UUID, String> names = names(java.util.Arrays.asList(i.getUploadedBy(), i.getDecidedBy(), i.getDuplicateClearedBy()));
    VendorEntity vendor = i.getVendorId() == null ? null : vendors.findById(i.getVendorId()).orElse(null);
    boolean vendorVerified = vendor == null || vendor.isVerified();
    InvoiceEntity original = i.getDuplicateOf() == null ? null
        : invoices.findByIdAndOrganizationId(i.getDuplicateOf(), i.getOrganizationId()).orElse(null);
    DuplicateInfo duplicate = new DuplicateInfo(i.getDuplicateStatus(), original == null ? null : original.getId(),
        original == null ? null : original.displayLabel(), original == null ? null : original.getStatus().name(),
        i.getDuplicateClearedBy() == null ? null : names.getOrDefault(i.getDuplicateClearedBy(), "Unknown"),
        i.getDuplicateClearedAt(), i.getReusedFrom());
    return new InvoiceDetail(i.getId(), i.getStatus(), i.getDocumentType(), i.getOriginalFilename(), i.getContentType(),
        i.getFileSize(),
        i.getVendorName(), i.getInvoiceNumber(), i.getInvoiceDate(), i.getDueDate(), i.getCurrency(),
        i.getSubtotal(), i.getTax(), i.getTotal(),
        i.getVendorTaxId(),
        vendor == null ? null : new VendorRef(vendor.getId(), vendor.getName(), vendor.getTaxId(), vendor.getStatus().name()),
        duplicate,
        i.getFieldConfidence(), i.getModelReviewReasons(), i.isInjectionSuspected(), i.isHumanReviewed(),
        new OcrInfo(i.getOcrEngine(), i.getOcrConfidence(), i.getOcrPageCount(), i.getTextSource()), extractCheck(i),
        new UsageInfo(i.getLlmModel(), i.getInputTokens(), i.getOutputTokens(), i.getCostUsd()),
        i.getProcessingMs(), i.getFailureReason(),
        i.getUploadedBy(), names.getOrDefault(i.getUploadedBy(), "Unknown"),
        i.getDecidedBy() == null ? null : names.getOrDefault(i.getDecidedBy(), "Unknown"),
        i.getDecidedAt(), i.getDecisionComment(),
        i.getLineItems().stream().map(l -> new LineItemDto(l.getId(), l.getDescription(), l.getQuantity(),
            l.getUnitPrice(), l.getTotal())).toList(),
        i.getIssues().stream().map(x -> new IssueDto(x.getCode(), x.getSeverity(), x.getField(), x.getMessage(),
            x.getRelatedInvoiceId())).toList(),
        permissions(i, viewer, vendorVerified), i.getCreatedAt(), i.getUpdatedAt(), i.getVersion());
  }

  static ExtractCheck extractCheck(InvoiceEntity i) {
    Map<String, Object> v = i.getExtractValidation();
    if (v == null || !(v.get("status") instanceof String status)) return null;
    int errors = 0, warnings = 0;
    if (v.get("issues") instanceof List<?> issues) {
      for (Object o : issues) {
        if (o instanceof Map<?, ?> m) {
          if ("error".equals(m.get("severity"))) errors++;
          else warnings++;
        }
      }
    }
    return new ExtractCheck(status, errors, warnings);
  }

  public static Permissions permissions(InvoiceEntity i, AuthPrincipal p, boolean vendorVerified) {
    boolean reviewer = p.role().isReviewer();
    boolean own = i.getUploadedBy().equals(p.userId());
    boolean canEdit = i.getStatus().isEditable() && (reviewer || own);
    String blocked = ApprovalRules.blockedReason(i, vendorVerified, p);
    boolean canApprove = blocked == null;
    boolean canReject = reviewer && InvoiceStatus.REJECTABLE.contains(i.getStatus());
    boolean canRetry = i.getStatus() == InvoiceStatus.FAILED && (reviewer || own);
    boolean canDelete = p.role() == Role.ADMIN && i.getStatus() != InvoiceStatus.APPROVED;
    boolean canDecideDuplicate = canEdit && i.getDuplicateStatus() == DuplicateStatus.POSSIBLE_DUPLICATE;
    return new Permissions(canEdit, canApprove, canReject, canRetry, canDelete, canDecideDuplicate,
        reviewer && InvoiceStatus.AWAITING_DECISION.contains(i.getStatus()) ? blocked : null);
  }


  public Map<UUID, String> names(Collection<UUID> ids) {
    List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (distinct.isEmpty()) return Map.of();
    return users.findAllById(distinct).stream()
        .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName, (a, b) -> a));
  }
}
