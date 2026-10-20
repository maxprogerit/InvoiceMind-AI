package com.invoicemind.ai.notification;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
  public record NotificationDto(@NotNull UUID id, @NotNull UUID organizationId, @NotNull String type, @NotNull String title, @NotNull String body,
                                UUID invoiceId, @NotNull boolean read, @NotNull Instant createdAt) {}

  private final NotificationRepository repository;
  private final SimpMessagingTemplate messaging;

  /** Persists the notification and pushes it to the recipient's open WebSocket sessions after commit. */
  @Transactional
  public void notify(UUID organizationId, UUID userId, String type, String title, String body, UUID invoiceId) {
    NotificationEntity n = new NotificationEntity();
    n.setOrganizationId(organizationId);
    n.setUserId(userId);
    n.setType(type);
    n.setTitle(title);
    n.setBody(body.length() > 500 ? body.substring(0, 497) + "..." : body);
    n.setInvoiceId(invoiceId);
    NotificationDto dto = toDto(repository.save(n));
    afterCommit(() -> messaging.convertAndSendToUser(userId.toString(), "/queue/notifications", dto));
  }

  @Transactional(readOnly = true)
  public java.util.List<NotificationDto> latest(UUID userId, UUID organizationId) {
    return repository.findTop50ByUserIdAndOrganizationIdOrderByCreatedAtDesc(userId, organizationId).stream().map(NotificationService::toDto).toList();
  }

  @Transactional(readOnly = true)
  public long unread(UUID userId, UUID organizationId) {
    return repository.countByUserIdAndOrganizationIdAndReadAtIsNull(userId, organizationId);
  }

  @Transactional
  public boolean markRead(UUID userId, UUID organizationId, UUID id) {
    return repository.findByIdAndUserIdAndOrganizationId(id, userId, organizationId).map(n -> {
      if (n.getReadAt() == null) n.setReadAt(Instant.now());
      return true;
    }).orElse(false);
  }

  @Transactional
  public int markAllRead(UUID userId, UUID organizationId) {
    return repository.markAllRead(userId, organizationId, Instant.now());
  }

  static NotificationDto toDto(NotificationEntity n) {
    return new NotificationDto(n.getId(), n.getOrganizationId(), n.getType(), n.getTitle(), n.getBody(), n.getInvoiceId(),
        n.getReadAt() != null, n.getCreatedAt());
  }

  private void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() { safely(action); }
      });
    } else {
      safely(action);
    }
  }

  private void safely(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      log.warn("WebSocket push failed: {}", e.getMessage());
    }
  }
}
