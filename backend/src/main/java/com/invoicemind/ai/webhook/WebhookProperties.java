package com.invoicemind.ai.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Outbound webhooks. {@code allowPrivateTargets} is off by default: a webhook URL is typed in by an admin but
 * fetched by the server, so private, loopback and link-local addresses (internal services, cloud metadata) are
 * refused. Switch it on for a self-hosted ERP inside your own network.
 */
@ConfigurationProperties(prefix = "app.webhooks")
public record WebhookProperties(
    @DefaultValue("false") boolean allowPrivateTargets,
    @DefaultValue("8000") int timeoutMs,
    @DefaultValue("8") int maxAttempts,
    @DefaultValue("30") int retryBaseSeconds,
    @DefaultValue("2000") int pollMs) {}
