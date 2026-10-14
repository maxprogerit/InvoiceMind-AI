package com.invoicemind.ai.common;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(Instant timestamp, int status, String code, String message, String path,
                            String requestId, Map<String, String> fieldErrors) {}
