package com.invoicemind.ai.invoice;

import com.invoicemind.ai.audit.AuditService.AuditEntryDto;
import com.invoicemind.ai.common.PageResponse;
import com.invoicemind.ai.invoice.InvoiceDtos.DecisionRequest;
import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceDetail;
import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceSummary;
import com.invoicemind.ai.invoice.InvoiceDtos.OcrTextResponse;
import com.invoicemind.ai.invoice.InvoiceDtos.UpdateInvoiceRequest;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Invoices")
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {
  private final InvoiceService service;

  /** Accepts the file and returns immediately; extraction continues asynchronously (see status events). */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  public InvoiceSummary upload(@RequestPart("file") MultipartFile file) {
    return service.upload(file, CurrentUser.get());
  }

  @GetMapping
  public PageResponse<InvoiceSummary> list(
      @RequestParam(required = false) List<InvoiceStatus> status,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String vendor,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) BigDecimal minTotal,
      @RequestParam(required = false) BigDecimal maxTotal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort) {
    var filter = new InvoiceFilter(status, q, vendor, currency, from, to, minTotal, maxTotal);
    return service.list(filter, CurrentUser.get(), page, size, sort);
  }

  @GetMapping("/{id}")
  public InvoiceDetail get(@PathVariable UUID id) {
    return service.get(id, CurrentUser.get());
  }

  @PutMapping("/{id}")
  public InvoiceDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateInvoiceRequest request) {
    return service.update(id, request, CurrentUser.get());
  }

  @GetMapping("/{id}/ocr-text")
  public OcrTextResponse ocrText(@PathVariable UUID id) {
    return service.ocrText(id, CurrentUser.get());
  }

  @GetMapping("/{id}/audit")
  public List<AuditEntryDto> audit(@PathVariable UUID id) {
    return service.trail(id, CurrentUser.get());
  }

  /** The original upload, for the side-by-side preview. Served inline with sniffing disabled. */
  @GetMapping("/{id}/file")
  public ResponseEntity<byte[]> file(@PathVariable UUID id) {
    var f = service.file(id, CurrentUser.get());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(f.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(f.filename(), StandardCharsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .cacheControl(CacheControl.noStore().cachePrivate())
        .body(f.data());
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
  public InvoiceDetail approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest request) {
    return service.approve(id, request, CurrentUser.get());
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
  public InvoiceDetail reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
    return service.reject(id, request, CurrentUser.get());
  }

  @PostMapping("/{id}/retry")
  public InvoiceDetail retry(@PathVariable UUID id) {
    return service.retry(id, CurrentUser.get());
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id, CurrentUser.get());
    return ResponseEntity.noContent().build();
  }
}
