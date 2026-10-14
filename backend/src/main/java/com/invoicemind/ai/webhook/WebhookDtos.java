package com.invoicemind.ai.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WebhookDtos {
  private WebhookDtos() {}

  /** Event types an endpoint can subscribe to. {@code ping} is only sent by the "send test" button. */
  public static final Set<String> EVENT_TYPES = Set.of("invoice.validated", "invoice.needs_review", "invoice.approved",
      "invoice.rejected", "invoice.failed");

  public record EndpointDto(@NotNull UUID id, @NotNull String url, String description, @NotNull List<String> events,
                            @NotNull boolean enabled, @NotNull Instant createdAt) {}

  /** The signing secret is only ever returned here, at creation. */
  public record CreatedEndpointDto(@NotNull EndpointDto endpoint, @NotNull String secret) {}

  public record CreateEndpointRequest(
      @NotBlank @Size(max = 500) String url,
      @Size(max = 200) String description,
      @NotEmpty List<String> events) {}

  public record UpdateEndpointRequest(
      @Size(max = 500) String url,
      @Size(max = 200) String description,
      List<String> events,
      Boolean enabled) {}

  public record DeliveryDto(@NotNull UUID id, @NotNull UUID eventId, @NotNull String eventType, UUID invoiceId,
                            @NotNull String status, @NotNull int attempts, Integer lastStatusCode, String lastError,
                            @NotNull Instant createdAt, Instant deliveredAt, Instant nextAttemptAt) {}
}
