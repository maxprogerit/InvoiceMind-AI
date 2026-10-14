package com.invoicemind.ai.audit;

import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.user.UserRepository;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {
  public static final String INVOICE = "INVOICE";
  public static final String USER = "USER";
  public static final String EXPORT = "EXPORT";

  public record AuditEntryDto(@NotNull Long id, UUID userId, @NotNull String userName, @NotNull String action,
                              @NotNull String entityType, UUID entityId, Map<String, Object> details,
                              @NotNull Instant createdAt) {}

  private final AuditLogRepository repository;
  private final UserRepository users;

  /** Joins the caller's transaction so the audit row commits atomically with the change it describes. */
  @Transactional
  public void log(UUID organizationId, UUID userId, String action, String entityType, UUID entityId,
                  Map<String, Object> details) {
    AuditLogEntity e = new AuditLogEntity();
    e.setOrganizationId(organizationId);
    e.setUserId(userId);
    e.setAction(action);
    e.setEntityType(entityType);
    e.setEntityId(entityId);
    e.setDetails(details == null || details.isEmpty() ? null : details);
    repository.save(e);
  }

  @Transactional(readOnly = true)
  public List<AuditEntryDto> trailFor(UUID organizationId, String entityType, UUID entityId) {
    return toDtos(repository.findByOrganizationIdAndEntityTypeAndEntityIdOrderByCreatedAtAscIdAsc(
        organizationId, entityType, entityId), organizationId);
  }

  /** Resolves actor names; rows without a user are system actions (the processing pipeline). */
  @Transactional(readOnly = true)
  public List<AuditEntryDto> toDtos(List<AuditLogEntity> rows, UUID organizationId) {
    Map<UUID, String> names = users.findAllById(rows.stream().map(AuditLogEntity::getUserId)
            .filter(Objects::nonNull).distinct().toList()).stream()
        .filter(u -> u.getOrganizationId().equals(organizationId))
        .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName, (a, b) -> a));
    return rows.stream().map(a -> new AuditEntryDto(a.getId(), a.getUserId(),
        a.getUserId() == null ? "System" : names.getOrDefault(a.getUserId(), "Unknown"),
        a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetails(), a.getCreatedAt())).toList();
  }
}
