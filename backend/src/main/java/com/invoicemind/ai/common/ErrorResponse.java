package com.invoicemind.ai.common;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

public record ErrorResponse(@NotNull Instant timestamp, @NotNull int status, @NotNull String code,
                            @NotNull String message, @NotNull String path, String requestId,
                            Map<String, String> fieldErrors) {}
