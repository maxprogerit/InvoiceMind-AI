package com.invoicemind.ai.common;

import jakarta.servlet.http.HttpServletRequest;
import com.invoicemind.ai.ai.AiClientException;
import com.invoicemind.ai.ratelimit.RateLimitedException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps exceptions to a stable JSON body. Internals (SQL, stack traces) are logged, never returned. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorResponse> api(ApiException ex, HttpServletRequest req) {
    return build(ex.status(), ex.code(), ex.getMessage(), req, null);
  }

  @ExceptionHandler(RateLimitedException.class)
  ResponseEntity<ErrorResponse> rateLimited(RateLimitedException ex, HttpServletRequest req) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.retryAfterSeconds()))
        .body(new ErrorResponse(Instant.now(), 429, "rate_limited", ex.getMessage(), req.getRequestURI(),
            MDC.get(RequestIdFilter.MDC_KEY), null));
  }

  /** The AI service failed for a synchronous request (knowledge base). The message was written to be user-safe. */
  @ExceptionHandler(AiClientException.class)
  ResponseEntity<ErrorResponse> ai(AiClientException ex, HttpServletRequest req) {
    boolean unavailable = ex.status() == 503 || ex.status() == 0 || ex.code().equals("ai_circuit_open");
    return build(unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY, ex.code(), ex.getMessage(), req, null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
    return build(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", req, fields);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ErrorResponse> constraint(ConstraintViolationException ex, HttpServletRequest req) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getConstraintViolations().forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
    return build(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", req, fields);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class})
  ResponseEntity<ErrorResponse> badRequest(Exception ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, "bad_request", "Malformed request", req, null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ErrorResponse> denied(AccessDeniedException ex, HttpServletRequest req) {
    return build(HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to perform this action", req, null);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
    return build(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large", "File exceeds the 15 MB limit", req, null);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> mediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
    return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Unsupported content type", req, null);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
    return build(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "Method not allowed", req, null);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> noResource(NoResourceFoundException ex, HttpServletRequest req) {
    return build(HttpStatus.NOT_FOUND, "not_found", "Resource not found", req, null);
  }

  @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
  ResponseEntity<ErrorResponse> conflict(Exception ex, HttpServletRequest req) {
    log.warn("Data conflict: {}", ex.getClass().getSimpleName());
    return build(HttpStatus.CONFLICT, "conflict", "The request conflicts with the current state, please retry", req, null);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest req) {
    log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error", req, null);
  }

  private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                              HttpServletRequest req, Map<String, String> fields) {
    return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(), code, message,
        req.getRequestURI(), MDC.get(RequestIdFilter.MDC_KEY), fields));
  }
}
