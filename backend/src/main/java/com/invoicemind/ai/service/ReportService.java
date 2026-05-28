package com.invoicemind.ai.service;

import com.invoicemind.ai.audit.AuditLogService;
import com.invoicemind.ai.entity.ReportEntity;
import com.invoicemind.ai.repository.InvoiceRepository;
import com.invoicemind.ai.repository.ReportRepository;
import com.invoicemind.ai.repository.VendorRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportService {
  private final ReportRepository reportRepository;
  private final InvoiceRepository invoiceRepository;
  private final VendorRepository vendorRepository;
  private final AuditLogService auditLogService;

  public List<ReportEntity> listAll() {
    return reportRepository.findByOrganizationIdOrderByCreatedAtDesc(1L);
  }

  public ReportEntity generate(String reportType, String format, LocalDate dateFrom, LocalDate dateTo, Long userId) {
    ReportEntity report = new ReportEntity();
    report.setOrganizationId(1L);
    report.setReportType(reportType);
    report.setFormat(format != null ? format : "csv");
    report.setGeneratedBy("admin@invoicemind.ai");
    report.setCreatedAt(Instant.now());
    report.setStatus("completed");
    report.setDateFrom(dateFrom);
    report.setDateTo(dateTo);
    int records = reportType.contains("vendor") ? (int) vendorRepository.count()
        : (int) invoiceRepository.count();
    report.setRecordCount(records);
    report.setStoragePath("/reports/" + reportType + "_" + Instant.now().toEpochMilli() + "." + report.getFormat());
    ReportEntity saved = reportRepository.save(report);
    auditLogService.log(userId, "REPORT_GENERATED", "report", saved.getId(),
        "{\"type\":\"" + reportType + "\",\"format\":\"" + format + "\"}");
    return saved;
  }

  public byte[] downloadCsv(Long reportId) {
    ReportEntity report = reportRepository.findById(reportId).orElseThrow();
    StringBuilder sb = new StringBuilder();
    if (report.getReportType().contains("invoice") || report.getReportType().contains("monthly")) {
      sb.append("ID,Invoice Number,Vendor,Amount,Currency,Status\n");
      invoiceRepository.findByOrganizationIdOrderByIdDesc(1L).forEach(inv ->
          sb.append(inv.getId()).append(",").append(inv.getInvoiceNumber()).append(",")
              .append(inv.getVendorId()).append(",").append(inv.getTotalAmount()).append(",")
              .append(inv.getCurrency()).append(",").append(inv.getStatus()).append("\n"));
    } else if (report.getReportType().contains("vendor")) {
      sb.append("ID,Name,Email,Payment Terms,Risk Score\n");
      vendorRepository.findByOrganizationIdOrderByNameAsc(1L).forEach(v ->
          sb.append(v.getId()).append(",\"").append(v.getName()).append("\",")
              .append(v.getContactEmail() != null ? v.getContactEmail() : "").append(",")
              .append(v.getPaymentTerms() != null ? v.getPaymentTerms() : "").append(",")
              .append(v.getRiskScore()).append("\n"));
    } else {
      sb.append("Report: ").append(report.getReportType()).append("\nGenerated: ").append(report.getCreatedAt());
    }
    return sb.toString().getBytes();
  }
}
