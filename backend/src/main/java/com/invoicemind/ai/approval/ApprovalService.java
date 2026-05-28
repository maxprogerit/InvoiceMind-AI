package com.invoicemind.ai.approval;

import com.invoicemind.ai.audit.AuditLogService;
import com.invoicemind.ai.entity.ApprovalEntity;
import com.invoicemind.ai.repository.ApprovalRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApprovalService {
  private final ApprovalRepository approvalRepository;
  @Lazy private final AuditLogService auditLogService;

  public List<ApprovalEntity> pending() { return approvalRepository.findByStatusOrderByIdDesc("pending"); }

  public List<ApprovalEntity> listAll() { return approvalRepository.findByOrganizationIdOrderByIdDesc(1L); }

  public ApprovalEntity getById(Long id) { return approvalRepository.findById(id).orElseThrow(); }

  public ApprovalEntity decide(Long approvalId, String decision, String comment, Long approverId) {
    ApprovalEntity approval = approvalRepository.findById(approvalId).orElseThrow();
    approval.setStatus(decision);
    approval.setComment(comment);
    approval.setApproverId(approverId);
    approval.setDecidedAt(Instant.now());
    ApprovalEntity saved = approvalRepository.save(approval);
    auditLogService.log(approverId, "APPROVAL_DECISION", "approval", approvalId,
        "{\"decision\":\"" + decision + "\",\"invoice_id\":" + approval.getInvoiceId() + "}");
    return saved;
  }

  public ApprovalEntity createForInvoice(Long invoiceId, Long requestedBy) {
    ApprovalEntity approval = new ApprovalEntity();
    approval.setOrganizationId(1L);
    approval.setInvoiceId(invoiceId);
    approval.setRequestedBy(requestedBy != null ? requestedBy : 1L);
    approval.setStatus("pending");
    approval.setAiRecommendation("Review required — confidence below threshold or high value invoice");
    ApprovalEntity saved = approvalRepository.save(approval);
    auditLogService.log(null, "APPROVAL_REQUESTED", "approval", saved.getId(),
        "{\"invoice_id\":" + invoiceId + "}");
    return saved;
  }

  public long countPending() { return approvalRepository.countByOrganizationIdAndStatus(1L, "pending"); }
}
