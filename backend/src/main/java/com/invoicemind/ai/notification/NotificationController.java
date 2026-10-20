package com.invoicemind.ai.notification;

import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.notification.NotificationService.NotificationDto;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
  private final NotificationService service;

  public record NotificationList(@NotNull List<NotificationDto> items, @NotNull long unreadCount) {}

  @GetMapping
  public NotificationList list() {
    var me = CurrentUser.get();
    return new NotificationList(service.latest(me.userId(), me.organizationId()), service.unread(me.userId(), me.organizationId()));
  }

  @PostMapping("/{id}/read")
  public ResponseEntity<Void> markRead(@PathVariable UUID id) {
    if (!service.markRead(CurrentUser.get().userId(), CurrentUser.get().organizationId(), id)) throw ApiException.notFound("Notification");
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/read-all")
  public ResponseEntity<Void> markAllRead() {
    service.markAllRead(CurrentUser.get().userId(), CurrentUser.get().organizationId());
    return ResponseEntity.noContent().build();
  }
}
