package com.invoicemind.ai.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.invoice.InvoiceEntity;
import com.invoicemind.ai.invoice.InvoiceFilter;
import com.invoicemind.ai.invoice.InvoiceMapper;
import com.invoicemind.ai.invoice.InvoiceService;
import com.invoicemind.ai.invoice.LineItemEntity;
import com.invoicemind.ai.invoice.ValidationIssueEntity;
import com.invoicemind.ai.security.AuthPrincipal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports the invoices visible to the caller (same tenant/ownership scope and filters as the list).
 * Text cells are neutralised against spreadsheet formula injection because vendor names and
 * descriptions originate from OCR of untrusted documents.
 */
@Service
@RequiredArgsConstructor
public class ExportService {
  public static final int MAX_ROWS = 10_000;

  public enum Format {
    CSV("text/csv; charset=utf-8", "csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    JSON("application/json", "json");

    public final String contentType;
    public final String extension;

    Format(String contentType, String extension) {
      this.contentType = contentType;
      this.extension = extension;
    }
  }

  public record ExportFile(byte[] data, Format format, int rows) {}

  /** JSON export row (no permissions or storage internals). */
  public record ExportInvoice(UUID id, String status, String vendor, String invoiceNumber, LocalDate invoiceDate,
                              LocalDate dueDate, String currency, BigDecimal subtotal, BigDecimal tax,
                              BigDecimal total, String file, Instant uploadedAt, Instant decidedAt,
                              String decisionComment, List<String> issues, List<ExportLine> lineItems) {}

  public record ExportLine(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {}

  private static final String[] HEADERS = {"ID", "Status", "Vendor", "Invoice number", "Invoice date", "Due date",
      "Currency", "Subtotal", "Tax", "Total", "Uploaded by", "Uploaded at", "Decided at", "Decision comment",
      "Issues", "File"};

  private final InvoiceService invoiceService;
  private final InvoiceMapper mapper;
  private final AuditService audit;
  private final ObjectMapper objectMapper;

  @Transactional
  public ExportFile export(Format format, InvoiceFilter filter, AuthPrincipal me) {
    List<InvoiceEntity> rows = invoiceService.listForExport(filter, me, MAX_ROWS);
    Map<UUID, String> names = mapper.names(rows.stream().map(InvoiceEntity::getUploadedBy).toList());
    byte[] data = switch (format) {
      case CSV -> csv(rows, names);
      case XLSX -> xlsx(rows, names);
      case JSON -> json(rows);
    };
    audit.log(me.organizationId(), me.userId(), "EXPORT_CREATED", AuditService.EXPORT, null,
        Map.of("format", format.name(), "rows", rows.size()));
    return new ExportFile(data, format, rows.size());
  }

  // ------------------------------------------------------------------- csv
  private byte[] csv(List<InvoiceEntity> rows, Map<UUID, String> names) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
         OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
         CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {
      out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // BOM so Excel detects UTF-8
      for (InvoiceEntity i : rows) {
        printer.printRecord(i.getId(), i.getStatus(), safe(i.getVendorName()), safe(i.getInvoiceNumber()),
            i.getInvoiceDate(), i.getDueDate(), i.getCurrency(), plain(i.getSubtotal()), plain(i.getTax()),
            plain(i.getTotal()), safe(names.getOrDefault(i.getUploadedBy(), "")), i.getCreatedAt(), i.getDecidedAt(),
            safe(i.getDecisionComment()), safe(issues(i)), safe(i.getOriginalFilename()));
      }
      printer.flush();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ------------------------------------------------------------------ xlsx
  private byte[] xlsx(List<InvoiceEntity> rows, Map<UUID, String> names) {
    try (SXSSFWorkbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      CreationHelper helper = wb.getCreationHelper();
      Font bold = wb.createFont();
      bold.setBold(true);
      CellStyle header = wb.createCellStyle();
      header.setFont(bold);
      CellStyle money = wb.createCellStyle();
      money.setDataFormat(helper.createDataFormat().getFormat("#,##0.00"));
      CellStyle date = wb.createCellStyle();
      date.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
      CellStyle stamp = wb.createCellStyle();
      stamp.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));

      Sheet sheet = wb.createSheet("Invoices");
      Row head = sheet.createRow(0);
      for (int c = 0; c < HEADERS.length; c++) {
        head.createCell(c).setCellValue(HEADERS[c]);
        head.getCell(c).setCellStyle(header);
      }
      int r = 1;
      for (InvoiceEntity i : rows) {
        Row row = sheet.createRow(r++);
        int c = 0;
        row.createCell(c++).setCellValue(i.getId().toString());
        row.createCell(c++).setCellValue(i.getStatus().name());
        row.createCell(c++).setCellValue(nz(i.getVendorName()));
        row.createCell(c++).setCellValue(nz(i.getInvoiceNumber()));
        if (i.getInvoiceDate() != null) {
          var cell = row.createCell(c);
          cell.setCellValue(i.getInvoiceDate());
          cell.setCellStyle(date);
        }
        c++;
        if (i.getDueDate() != null) {
          var cell = row.createCell(c);
          cell.setCellValue(i.getDueDate());
          cell.setCellStyle(date);
        }
        c++;
        row.createCell(c++).setCellValue(nz(i.getCurrency()));
        for (BigDecimal amount : new BigDecimal[]{i.getSubtotal(), i.getTax(), i.getTotal()}) {
          if (amount != null) {
            var cell = row.createCell(c);
            cell.setCellValue(amount.doubleValue());
            cell.setCellStyle(money);
          }
          c++;
        }
        row.createCell(c++).setCellValue(names.getOrDefault(i.getUploadedBy(), ""));
        var uploaded = row.createCell(c++);
        uploaded.setCellValue(java.time.LocalDateTime.ofInstant(i.getCreatedAt(), java.time.ZoneOffset.UTC));
        uploaded.setCellStyle(stamp);
        if (i.getDecidedAt() != null) {
          var cell = row.createCell(c);
          cell.setCellValue(java.time.LocalDateTime.ofInstant(i.getDecidedAt(), java.time.ZoneOffset.UTC));
          cell.setCellStyle(stamp);
        }
        c++;
        row.createCell(c++).setCellValue(nz(i.getDecisionComment()));
        row.createCell(c++).setCellValue(issues(i));
        row.createCell(c).setCellValue(i.getOriginalFilename());
      }
      sheet.createFreezePane(0, 1);
      sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(r - 1, 0), 0, HEADERS.length - 1));
      int[] widths = {38, 14, 28, 18, 13, 13, 9, 14, 12, 14, 22, 18, 18, 30, 40, 28};
      for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);

      Sheet lines = wb.createSheet("Line items");
      String[] lh = {"Invoice ID", "Invoice number", "Vendor", "#", "Description", "Quantity", "Unit price", "Total"};
      Row lhead = lines.createRow(0);
      for (int c = 0; c < lh.length; c++) {
        lhead.createCell(c).setCellValue(lh[c]);
        lhead.getCell(c).setCellStyle(header);
      }
      int lr = 1;
      for (InvoiceEntity i : rows) {
        for (LineItemEntity l : i.getLineItems()) {
          Row row = lines.createRow(lr++);
          row.createCell(0).setCellValue(i.getId().toString());
          row.createCell(1).setCellValue(nz(i.getInvoiceNumber()));
          row.createCell(2).setCellValue(nz(i.getVendorName()));
          row.createCell(3).setCellValue(l.getPosition() + 1);
          row.createCell(4).setCellValue(l.getDescription());
          if (l.getQuantity() != null) row.createCell(5).setCellValue(l.getQuantity().doubleValue());
          for (int k = 0; k < 2; k++) {
            BigDecimal v = k == 0 ? l.getUnitPrice() : l.getTotal();
            if (v != null) {
              var cell = row.createCell(6 + k);
              cell.setCellValue(v.doubleValue());
              cell.setCellStyle(money);
            }
          }
        }
      }
      lines.createFreezePane(0, 1);
      int[] lw = {38, 18, 28, 5, 50, 10, 14, 14};
      for (int c = 0; c < lw.length; c++) lines.setColumnWidth(c, lw[c] * 256);

      wb.write(out);
      wb.dispose();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ------------------------------------------------------------------ json
  private byte[] json(List<InvoiceEntity> rows) {
    List<ExportInvoice> items = rows.stream().map(i -> new ExportInvoice(i.getId(), i.getStatus().name(),
        i.getVendorName(), i.getInvoiceNumber(), i.getInvoiceDate(), i.getDueDate(), i.getCurrency(),
        i.getSubtotal(), i.getTax(), i.getTotal(), i.getOriginalFilename(), i.getCreatedAt(), i.getDecidedAt(),
        i.getDecisionComment(),
        i.getIssues().stream().map(x -> x.getSeverity() + ":" + x.getCode()).toList(),
        i.getLineItems().stream().map(l -> new ExportLine(l.getDescription(), l.getQuantity(), l.getUnitPrice(),
            l.getTotal())).toList())).toList();
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(items);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // --------------------------------------------------------------- helpers
  /** Prefixes cells a spreadsheet would interpret as a formula (OWASP CSV-injection guidance). */
  static String safe(String value) {
    if (value == null || value.isEmpty()) return value;
    char first = value.charAt(0);
    return (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r')
        ? "'" + value : value;
  }

  private static String plain(BigDecimal v) { return v == null ? null : v.toPlainString(); }

  private static String nz(String s) { return s == null ? "" : s; }

  private static String issues(InvoiceEntity i) {
    return i.getIssues().stream().map(ValidationIssueEntity::getCode).distinct().collect(Collectors.joining("; "));
  }
}
