package com.invoicemind.ai.audit;

import com.invoicemind.ai.entity.AuditLogEntity;
import com.invoicemind.ai.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {
  private final AuditLogRepository auditLogRepository;

  public void log(Long userId, String action, String entityType, Long entityId, String metadata) {
    AuditLogEntity log = new AuditLogEntity();
    log.setOrganizationId(1L);
    log.setUserId(userId);
    log.setAction(action);
    log.setEntityType(entityType);
    log.setEntityId(entityId);
    log.setMetadataJson(metadata);
    log.setCreatedAt(Instant.now());
    auditLogRepository.save(log);
  }

  public List<AuditLogEntity> recent() {
    return auditLogRepository.findTop50ByOrderByCreatedAtDesc();
  }

  public List<AuditLogEntity> listPaged(org.springframework.data.domain.PageRequest page) {
    return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(1L, page);
  }
}
