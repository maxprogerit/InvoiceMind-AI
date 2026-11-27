package com.invoicemind.ai.export;

import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.export.ExportService.ExportFile;
import com.invoicemind.ai.export.ExportService.Format;
import com.invoicemind.ai.invoice.DocumentType;
import com.invoicemind.ai.invoice.InvoiceFilter;
import com.invoicemind.ai.invoice.InvoiceStatus;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Export")
@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
public class ExportController {
  private final ExportService service;
  private final AccountingExportService accounting;

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
      @RequestParam(required = false) BigDecimal maxTotal,
      @RequestParam(required = false) DocumentType documentType) {
    Format fmt;
    try {
      fmt = Format.valueOf(format.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("invalid_format", "format must be one of: csv, xlsx, json");
    }
    var filter = new InvoiceFilter(status, q, vendor, currency, from, to, minTotal, maxTotal, documentType);
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

  // -------------------------------------------------------------- accounting
  public record AccountingExportDto(@jakarta.validation.constraints.NotNull UUID batchId,
                                    @jakarta.validation.constraints.NotNull int invoices,
                                    @jakarta.validation.constraints.NotNull List<AccountingExportService.ExportSkipped> skipped) {}

  public record PendingDto(@jakarta.validation.constraints.NotNull long approvedNotExported) {}

  @GetMapping("/accounting/pending")
  public PendingDto pending() {
    return new PendingDto(accounting.pending(CurrentUser.get()));
  }

  /** Exports every approved, not yet exported invoice as a Xero or QuickBooks Online bill import, and marks them. */
  @PostMapping("/accounting")
  public AccountingExportDto exportToAccounting(@jakarta.validation.Valid @RequestBody AccountingExportService.Options options) {
    var result = accounting.export(options, CurrentUser.get());
    return new AccountingExportDto(result.batchId(), result.invoices(), result.skipped());
  }

  @GetMapping("/accounting/batches")
  public List<AccountingExportService.BatchDto> batches() {
    return accounting.batches(CurrentUser.get());
  }

  /** The CSV of a batch (again, as often as needed; nothing is marked twice). */
  @GetMapping("/accounting/batches/{id}/file")
  public ResponseEntity<byte[]> batchFile(@PathVariable UUID id) {
    var result = accounting.download(id, CurrentUser.get());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.filename()).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore().cachePrivate())
        .body(result.data());
  }
}
