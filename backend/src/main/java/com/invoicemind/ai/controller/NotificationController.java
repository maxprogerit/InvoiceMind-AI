package com.invoicemind.ai.controller;

import com.invoicemind.ai.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
  private final NotificationService notificationService;
  @GetMapping
  public Object list() { return notificationService.list(); }
}
