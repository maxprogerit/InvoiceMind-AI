package com.invoicemind.ai.export;

import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.export.ExportService.ExportFile;
import com.invoicemind.ai.export.ExportService.Format;
import com.invoicemind.ai.invoice.InvoiceFilter;
import com.invoicemind.ai.invoice.InvoiceStatus;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Export")
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class ExportController {
  private final ExportService service;

  /** Same filters as the invoice list; at most {@value ExportService#MAX_ROWS} rows. */
  @GetMapping("/invoices")
  public ResponseEntity<byte[]> invoices(
      @RequestParam(defaultValue = "xlsx") String format,
      @RequestParam(required = false) List<InvoiceStatus> status,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String vendor,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) BigDecimal minTotal,
      @RequestParam(required = false) BigDecimal maxTotal) {
    Format fmt;
    try {
      fmt = Format.valueOf(format.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("invalid_format", "format must be one of: csv, xlsx, json");
    }
    var filter = new InvoiceFilter(status, q, vendor, currency, from, to, minTotal, maxTotal);
    ExportFile file = service.export(fmt, filter, CurrentUser.get());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(fmt.contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("invoices-" + LocalDate.now() + "." + fmt.extension).build().toString())
        .header("X-Row-Count", String.valueOf(file.rows()))
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore().cachePrivate())
        .body(file.data());
  }
}
