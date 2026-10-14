package com.invoicemind.ai.audit;

import com.invoicemind.ai.audit.AuditService.AuditEntryDto;
import com.invoicemind.ai.common.PageResponse;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit")
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {
  private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

  private final AuditLogRepository repository;
  private final AuditService auditService;

  /** Organisation-wide audit trail. Reviewers only; per-invoice trails are served by the invoice endpoints. */
  @GetMapping
  @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
  public PageResponse<AuditEntryDto> list(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    AuthPrincipal me = CurrentUser.get();
    Instant start = from == null ? Instant.EPOCH : from.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant end = to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var result = repository.search(me.organizationId(), blankToNull(action), blankToNull(entityType), userId,
        start, end, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    return PageResponse.of(result, auditService.toDtos(result.getContent(), me.organizationId()));
  }

  private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
