package com.invoicemind.ai.notification;

import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.notification.NotificationService.NotificationDto;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
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

  public record NotificationList(List<NotificationDto> items, long unreadCount) {}

  @GetMapping
  public NotificationList list() {
    UUID me = CurrentUser.get().userId();
    return new NotificationList(service.latest(me), service.unread(me));
  }

  @PostMapping("/{id}/read")
  public ResponseEntity<Void> markRead(@PathVariable UUID id) {
    if (!service.markRead(CurrentUser.get().userId(), id)) throw ApiException.notFound("Notification");
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/read-all")
  public ResponseEntity<Void> markAllRead() {
    service.markAllRead(CurrentUser.get().userId());
    return ResponseEntity.noContent().build();
  }
}
