package com.invoicemind.ai.invoice;

import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceDetail;
import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceSummary;
import com.invoicemind.ai.invoice.InvoiceDtos.IssueDto;
import com.invoicemind.ai.invoice.InvoiceDtos.LineItemDto;
import com.invoicemind.ai.invoice.InvoiceDtos.OcrInfo;
import com.invoicemind.ai.invoice.InvoiceDtos.Permissions;
import com.invoicemind.ai.invoice.InvoiceDtos.UsageInfo;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.user.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Entity -> DTO mapping. Call inside a transaction: it touches lazy collections. */
@Component
@RequiredArgsConstructor
public class InvoiceMapper {
  private final UserRepository users;

  public List<InvoiceSummary> toSummaries(Collection<InvoiceEntity> invoices) {
    Map<UUID, String> names = names(invoices.stream().map(InvoiceEntity::getUploadedBy).toList());
    return invoices.stream().map(i -> toSummary(i, names.getOrDefault(i.getUploadedBy(), "Unknown"))).toList();
  }

  public InvoiceSummary toSummary(InvoiceEntity i, String uploaderName) {
    int errors = (int) i.getIssues().stream().filter(x -> "ERROR".equals(x.getSeverity())).count();
    return new InvoiceSummary(i.getId(), i.getStatus(), i.getOriginalFilename(), i.getVendorName(),
        i.getInvoiceNumber(), i.getInvoiceDate(), i.getDueDate(), i.getCurrency(), i.getTotal(),
        i.getUploadedBy(), uploaderName, errors, i.getIssues().size() - errors, i.getCreatedAt());
  }

  public InvoiceDetail toDetail(InvoiceEntity i, AuthPrincipal viewer) {
    Map<UUID, String> names = names(java.util.Arrays.asList(i.getUploadedBy(), i.getDecidedBy()));
    return new InvoiceDetail(i.getId(), i.getStatus(), i.getOriginalFilename(), i.getContentType(), i.getFileSize(),
        i.getVendorName(), i.getInvoiceNumber(), i.getInvoiceDate(), i.getDueDate(), i.getCurrency(),
        i.getSubtotal(), i.getTax(), i.getTotal(),
        i.getFieldConfidence(), i.getModelReviewReasons(), i.isInjectionSuspected(), i.isHumanReviewed(),
        new OcrInfo(i.getOcrEngine(), i.getOcrConfidence(), i.getOcrPageCount()),
        new UsageInfo(i.getLlmModel(), i.getInputTokens(), i.getOutputTokens(), i.getCostUsd()),
        i.getProcessingMs(), i.getFailureReason(),
        i.getUploadedBy(), names.getOrDefault(i.getUploadedBy(), "Unknown"),
        i.getDecidedBy() == null ? null : names.getOrDefault(i.getDecidedBy(), "Unknown"),
        i.getDecidedAt(), i.getDecisionComment(),
        i.getLineItems().stream().map(l -> new LineItemDto(l.getId(), l.getDescription(), l.getQuantity(),
            l.getUnitPrice(), l.getTotal())).toList(),
        i.getIssues().stream().map(x -> new IssueDto(x.getCode(), x.getSeverity(), x.getField(), x.getMessage())).toList(),
        permissions(i, viewer), i.getCreatedAt(), i.getUpdatedAt(), i.getVersion());
  }

  public static Permissions permissions(InvoiceEntity i, AuthPrincipal p) {
    boolean reviewer = p.role().isReviewer();
    boolean own = i.getUploadedBy().equals(p.userId());
    boolean canEdit = i.getStatus().isEditable() && (reviewer || own);
    boolean canApprove = reviewer && i.getStatus() == InvoiceStatus.VALIDATED
        && !(p.role() == Role.APPROVER && own); // four-eyes: approvers cannot approve their own uploads
    boolean canReject = reviewer && InvoiceStatus.REJECTABLE.contains(i.getStatus());
    boolean canRetry = i.getStatus() == InvoiceStatus.FAILED && (reviewer || own);
    boolean canDelete = p.role() == Role.ADMIN && i.getStatus() != InvoiceStatus.APPROVED;
    return new Permissions(canEdit, canApprove, canReject, canRetry, canDelete);
  }

  public Map<UUID, String> names(Collection<UUID> ids) {
    List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
    if (distinct.isEmpty()) return Map.of();
    return users.findAllById(distinct).stream()
        .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName, (a, b) -> a));
  }
}
