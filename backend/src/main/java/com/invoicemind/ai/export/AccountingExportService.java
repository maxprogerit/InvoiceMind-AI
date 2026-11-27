package com.invoicemind.ai.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.invoice.InvoiceEntity;
import com.invoicemind.ai.invoice.InvoiceRepository;
import com.invoicemind.ai.invoice.InvoiceStatus;
import com.invoicemind.ai.invoice.LineItemEntity;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approved invoices out to accounting, as the bill-import CSV of Xero or of QuickBooks Online. Every exported
 * invoice is marked with its batch and is not exported again; a batch's file can be downloaded again.
 *
 * <p>Column sets (see docs/ACCOUNTING-EXPORT.md for sources and caveats):
 * <ul>
 *   <li>Xero: the bill import template's headers, asterisk = required by Xero. One row per line item; rows with the
 *       same InvoiceNumber and ContactName form one bill. Negative amounts (credit notes) import as credit notes.</li>
 *   <li>QuickBooks Online: Bill No, Supplier, Bill Date, Due Date, Account, Line Amount (+ Line Tax Code/Amount when
 *       sales tax/VAT is on). One row per line, each repeating bill number, supplier and date. QBO maps headings
 *       during the import. Credit notes are not supported by the QBO bill import and are left out (listed).</li>
 * </ul>
 * Text cells are neutralised against spreadsheet formula injection, as in every export.
 */
@Service
@RequiredArgsConstructor
public class AccountingExportService {
  public enum Target { XERO, QBO }

  /** How dates are written; the importers let the person pick the matching format. */
  public enum DateStyle {
    DMY("dd/MM/yyyy"), MDY("MM/dd/yyyy"), ISO("yyyy-MM-dd");

    final DateTimeFormatter formatter;

    DateStyle(String pattern) { this.formatter = DateTimeFormatter.ofPattern(pattern); }
  }

  /**
   * {@code account}: the account (code for Xero, name for QBO) used for lines of invoices without an expense
   * category; an invoice's category is used as its account when set. {@code taxCode}: Xero tax rate name / QBO tax
   * code, as set up in the accounting system.
   */
  public record Options(@NotNull Target target, @NotBlank @Size(max = 100) String account, @Size(max = 100) String taxCode,
                        @NotNull DateStyle dateStyle) {}

  public record ExportSkipped(@NotNull UUID id, @NotNull String label, @NotNull String reason) {}

  public record Result(UUID batchId, byte[] data, String filename, int invoices, List<ExportSkipped> skipped) {}

  public record BatchDto(@NotNull UUID id, @NotNull Target target, @NotNull int invoices, String createdByName,
                         @NotNull Instant createdAt) {}

  static final String[] XERO_HEADERS = {"*ContactName", "EmailAddress", "POAddressLine1", "POAddressLine2",
      "POAddressLine3", "POAddressLine4", "POCity", "PORegion", "POPostalCode", "POCountry", "*InvoiceNumber",
      "*InvoiceDate", "*DueDate", "Total", "InventoryItemCode", "Description", "*Quantity", "*UnitAmount",
      "*AccountCode", "*TaxType", "TaxAmount", "TrackingName1", "TrackingOption1", "TrackingName2", "TrackingOption2",
      "Currency"};

  static final String[] QBO_HEADERS = {"Bill No", "Supplier", "Bill Date", "Due Date", "Memo", "Account",
      "Line Description", "Line Amount", "Line Tax Code", "Line Tax Amount", "Currency"};

  private final InvoiceRepository invoices;
  private final NamedParameterJdbcTemplate jdbc;
  private final AuditService audit;
  private final ObjectMapper mapper;
  private final Clock clock;

  /** How many approved invoices are waiting to be exported (for the export page). */
  @Transactional(readOnly = true)
  public long pending(AuthPrincipal me) {
    return invoices.countByOrganizationIdAndStatusAndExportedAtIsNull(me.organizationId(), InvoiceStatus.APPROVED);
  }

  /** Exports every approved, not yet exported invoice and marks them, in one transaction. */
  @Transactional
  public Result export(Options options, AuthPrincipal me) {
    requireExporter(me);
    List<InvoiceEntity> ready = invoices.findForAccountingExport(me.organizationId(), InvoiceStatus.APPROVED);
    List<InvoiceEntity> included = new ArrayList<>();
    List<ExportSkipped> skipped = new ArrayList<>();
    for (InvoiceEntity i : ready) {
      String reason = skipReason(i, options.target());
      if (reason == null) included.add(i);
      else skipped.add(new ExportSkipped(i.getId(), i.displayLabel(), reason));
    }
    if (included.isEmpty()) {
      throw ApiException.conflict("nothing_to_export", skipped.isEmpty()
          ? "No approved invoices are waiting to be exported" : "None of the waiting invoices can be exported: " + skipped.get(0).reason());
    }
    UUID batch = UUID.randomUUID();
    Instant now = Instant.now(clock);
    jdbc.update("""
        insert into export_batches (id, organization_id, target, options, invoice_count, created_by, created_at)
        values (:id, :org, :target, cast(:options as jsonb), :count, :by, :at)""",
        new MapSqlParameterSource("id", batch).addValue("org", me.organizationId()).addValue("target", options.target().name())
            .addValue("options", json(options)).addValue("count", included.size()).addValue("by", me.userId())
            .addValue("at", Timestamp.from(now)));
    for (InvoiceEntity i : included) {
      i.setExportedAt(now);
      i.setExportBatchId(batch);
    }
    invoices.saveAll(included);
    audit.log(me.organizationId(), me.userId(), "INVOICES_EXPORTED", AuditService.EXPORT, batch,
        Map.of("target", options.target().name(), "invoices", included.size(), "skipped", skipped.size()));
    return new Result(batch, file(included, options), filename(options.target(), now), included.size(), skipped);
  }

  /** The file of an earlier batch, built again from the same invoices and options (nothing is marked). */
  @Transactional(readOnly = true)
  public Result download(UUID batchId, AuthPrincipal me) {
    requireExporter(me);
    Map<String, Object> row = jdbc.queryForList("select options, created_at from export_batches where id = :id and organization_id = :org",
        new MapSqlParameterSource("id", batchId).addValue("org", me.organizationId())).stream().findFirst()
        .orElseThrow(() -> ApiException.notFound("Export"));
    Options options = mapper.convertValue(parse(String.valueOf(row.get("options"))), Options.class);
    List<InvoiceEntity> members = invoices.findByOrganizationIdAndExportBatchId(me.organizationId(), batchId);
    members.sort(Comparator.comparing(InvoiceEntity::getCreatedAt));
    Instant at = ((Timestamp) row.get("created_at")).toInstant();
    return new Result(batchId, file(members, options), filename(options.target(), at), members.size(), List.of());
  }

  @Transactional(readOnly = true)
  public List<BatchDto> batches(AuthPrincipal me) {
    requireExporter(me);
    return jdbc.query("""
        select b.id, b.target, b.invoice_count, b.created_at, u.full_name from export_batches b
        left join users u on u.id = b.created_by where b.organization_id = :org order by b.created_at desc limit 50""",
        new MapSqlParameterSource("org", me.organizationId()),
        (rs, n) -> new BatchDto(rs.getObject("id", UUID.class), Target.valueOf(rs.getString("target")),
            rs.getInt("invoice_count"), rs.getString("full_name"), rs.getTimestamp("created_at").toInstant()));
  }

  // ------------------------------------------------------------------ rules
  static String skipReason(InvoiceEntity i, Target target) {
    if (i.getVendorName() == null || i.getInvoiceNumber() == null || i.getInvoiceDate() == null || i.getTotal() == null) {
      return "Vendor, number, date or total is missing";
    }
    if (i.getCurrency() == null) return "The currency is missing";
    if (target == Target.QBO && i.getDocumentType().isCredit()) {
      return "QuickBooks Online does not import credit notes as bills; enter it as a supplier credit";
    }
    return null;
  }

  private static void requireExporter(AuthPrincipal me) {
    if (me.role() != Role.ADMIN && me.role() != Role.ACCOUNTANT) {
      throw ApiException.forbidden("Only accountants and admins export to accounting");
    }
  }

  // ------------------------------------------------------------------ files
  private byte[] file(List<InvoiceEntity> list, Options o) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
         OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
         CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
             .setHeader(o.target() == Target.XERO ? XERO_HEADERS : QBO_HEADERS).build())) {
      for (InvoiceEntity i : list) {
        String account = i.getExpenseCategory() != null ? i.getExpenseCategory() : o.account();
        String tax = o.taxCode() == null ? "" : o.taxCode();
        for (Line l : lines(i)) {
          if (o.target() == Target.XERO) {
            csv.printRecord(safe(i.getVendorName()), "", "", "", "", "", "", "", "", "", safe(i.getInvoiceNumber()),
                date(i.getInvoiceDate(), o), date(dueDate(i), o), "", "", safe(l.description()), plain(l.quantity()),
                plain(l.unitAmount()), safe(account), safe(tax), plain(l.taxAmount()), "", "", "", "", i.getCurrency());
          } else {
            csv.printRecord(safe(i.getInvoiceNumber()), safe(i.getVendorName()), date(i.getInvoiceDate(), o),
                date(dueDate(i), o), safe(memo(i)), safe(account), safe(l.description()), plain(l.amount()), safe(tax),
                plain(l.taxAmount()), i.getCurrency());
          }
        }
      }
      csv.flush();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A bill line in the importers' terms: net amounts, tax shown once per bill (on its first line). */
  record Line(String description, BigDecimal quantity, BigDecimal unitAmount, BigDecimal amount, BigDecimal taxAmount) {}

  static List<Line> lines(InvoiceEntity i) {
    List<Line> out = new ArrayList<>();
    BigDecimal tax = i.getTax();
    List<LineItemEntity> items = i.getLineItems();
    if (items.isEmpty()) {
      BigDecimal net = i.getSubtotal() != null ? i.getSubtotal() : i.getTotal().subtract(tax == null ? BigDecimal.ZERO : tax);
      out.add(new Line(i.displayLabel(), BigDecimal.ONE, net, net, tax));
      return out;
    }
    for (int n = 0; n < items.size(); n++) {
      LineItemEntity li = items.get(n);
      BigDecimal amount = li.getTotal() != null ? li.getTotal()
          : li.getQuantity() != null && li.getUnitPrice() != null ? li.getQuantity().multiply(li.getUnitPrice()).setScale(2, RoundingMode.HALF_UP)
          : BigDecimal.ZERO;
      BigDecimal qty = li.getQuantity() != null && li.getQuantity().signum() != 0 ? li.getQuantity().abs() : BigDecimal.ONE;
      BigDecimal unit = li.getUnitPrice() != null && li.getQuantity() != null && li.getQuantity().signum() != 0
          ? li.getUnitPrice().multiply(BigDecimal.valueOf(li.getQuantity().signum())) // credit notes: the sign rides on the price
          : amount.divide(qty, 4, RoundingMode.HALF_UP);
      out.add(new Line(li.getDescription(), qty, unit, amount, n == 0 ? tax : null));
    }
    return out;
  }

  private static LocalDate dueDate(InvoiceEntity i) {
    return i.getDueDate() != null ? i.getDueDate() : i.getInvoiceDate(); // both importers require a due date
  }

  private static String memo(InvoiceEntity i) {
    return "InvoiceMind " + i.getId().toString().substring(0, 8) + (i.getOriginalFilename() == null ? "" : " · " + i.getOriginalFilename());
  }

  private static String date(LocalDate d, Options o) { return d == null ? "" : d.format(o.dateStyle().formatter); }

  private static String plain(BigDecimal v) { return v == null ? "" : v.stripTrailingZeros().scale() < 2 ? v.setScale(2, RoundingMode.HALF_UP).toPlainString() : v.toPlainString(); }

  private static String safe(String v) { return ExportService.safe(v); }

  private static String filename(Target t, Instant at) {
    return (t == Target.XERO ? "xero-bills-" : "quickbooks-bills-") + at.toString().substring(0, 19).replace(':', '-') + ".csv";
  }

  private String json(Options o) {
    try {
      return mapper.writeValueAsString(o);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Map<String, Object> parse(String s) {
    try {
      return mapper.readValue(s, new TypeReference<>() {});
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
