package com.invoicemind.ai.webhook;

import com.invoicemind.ai.security.CurrentUser;
import com.invoicemind.ai.webhook.WebhookDtos.CreateEndpointRequest;
import com.invoicemind.ai.webhook.WebhookDtos.CreatedEndpointDto;
import com.invoicemind.ai.webhook.WebhookDtos.DeliveryDto;
import com.invoicemind.ai.webhook.WebhookDtos.EndpointDto;
import com.invoicemind.ai.webhook.WebhookDtos.UpdateEndpointRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Webhook endpoints and their delivery log. Admin only. */
@Tag(name = "Integrations")
@RestController
@RequestMapping("/api/v1/webhooks")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class WebhookController {
  private final WebhookService service;

  @GetMapping
  public List<EndpointDto> list() {
    return service.list(CurrentUser.get().organizationId());
  }

  @GetMapping("/events")
  public List<String> eventTypes() {
    return WebhookDtos.EVENT_TYPES.stream().sorted().toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreatedEndpointDto create(@Valid @RequestBody CreateEndpointRequest req) {
    return service.create(CurrentUser.get(), req);
  }

  @PatchMapping("/{id}")
  public EndpointDto update(@PathVariable UUID id, @Valid @RequestBody UpdateEndpointRequest req) {
    return service.update(CurrentUser.get(), id, req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(CurrentUser.get(), id);
  }

  @PostMapping("/{id}/test")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DeliveryDto test(@PathVariable UUID id) {
    return service.sendTest(CurrentUser.get(), id);
  }

  @GetMapping("/{id}/deliveries")
  public List<DeliveryDto> deliveries(@PathVariable UUID id) {
    return service.deliveries(CurrentUser.get().organizationId(), id);
  }

  @PostMapping("/{id}/deliveries/{deliveryId}/retry")
  public DeliveryDto retry(@PathVariable UUID id, @PathVariable UUID deliveryId) {
    return service.retry(CurrentUser.get(), id, deliveryId);
  }
}
