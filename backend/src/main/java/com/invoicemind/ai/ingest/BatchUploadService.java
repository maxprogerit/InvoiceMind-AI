package com.invoicemind.ai.ingest;

import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.config.AppProperties;
import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceSummary;
import com.invoicemind.ai.invoice.InvoiceService;
import com.invoicemind.ai.invoice.InvoiceSource;
import com.invoicemind.ai.ratelimit.RateLimiter;
import com.invoicemind.ai.security.AuthPrincipal;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Batch upload of a .zip archive: every PDF or image in it becomes an invoice through the normal upload path.
 *
 * <p>Limits, enforced while reading (the sizes a zip declares are not trusted): at most {@code max-entries} documents,
 * at most {@code max-total-bytes} uncompressed in total and the usual per-file limit. Entries with an absolute path,
 * a {@code ..} segment or a drive letter are refused (nothing is ever written under the entry's name, but such an
 * archive is not one a person made by zipping invoices); archives inside the archive are not opened; folders,
 * hidden files and macOS resource forks are skipped.
 */
@Slf4j
@Service
public class BatchUploadService {
  public record Skipped(@NotNull String name, @NotNull String reason) {}

  public record BatchResult(@NotNull List<InvoiceSummary> created, @NotNull List<Skipped> skipped) {}

  private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("zip", "7z", "rar", "gz", "tgz", "tar", "bz2", "xz", "jar");

  private final InvoiceService invoices;
  private final RateLimiter rateLimiter;
  private final AuditService audit;
  private final long maxFileBytes;
  private final int maxEntries;
  private final long maxTotalBytes;

  public BatchUploadService(InvoiceService invoices, RateLimiter rateLimiter, AuditService audit, AppProperties app,
                            @Value("${app.batch.max-entries:50}") int maxEntries,
                            @Value("${app.batch.max-total-bytes:209715200}") long maxTotalBytes) {
    this.invoices = invoices;
    this.rateLimiter = rateLimiter;
    this.audit = audit;
    this.maxFileBytes = app.storage().maxFileBytes();
    this.maxEntries = maxEntries;
    this.maxTotalBytes = maxTotalBytes;
  }

  public BatchResult uploadZip(MultipartFile zip, AuthPrincipal me) {
    rateLimiter.check("batch", me.userId().toString(), 5);
    if (zip == null || zip.isEmpty()) throw ApiException.badRequest("empty_file", "The archive is empty");
    List<InvoiceSummary> created = new ArrayList<>();
    List<Skipped> skipped = new ArrayList<>();
    long total = 0;
    int documents = 0;
    try (InputStream raw = zip.getInputStream()) {
      byte[] head = raw.readNBytes(4);
      if (head.length < 4 || head[0] != 'P' || head[1] != 'K' || head[2] != 3 || head[3] != 4) {
        throw new ApiException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, "not_a_zip",
            "Upload a .zip archive (or upload the documents themselves)");
      }
      try (ZipInputStream in = new ZipInputStream(new java.io.SequenceInputStream(new java.io.ByteArrayInputStream(head), raw))) {
        ZipEntry entry;
        while ((entry = in.getNextEntry()) != null) {
          String path = entry.getName();
          if (entry.isDirectory()) continue;
          String name = baseName(path);
          if (!safePath(path)) {
            skipped.add(new Skipped(path, "Unsafe path in the archive (absolute or with '..'); not imported"));
            continue;
          }
          if (name.isEmpty() || name.startsWith(".") || path.startsWith("__MACOSX/")) continue;
          if (ARCHIVE_EXTENSIONS.contains(extension(name))) {
            skipped.add(new Skipped(name, "Archives inside the archive are not opened"));
            continue;
          }
          if (documents >= maxEntries) {
            skipped.add(new Skipped(name, "More than " + maxEntries + " documents in one archive; the rest was not imported"));
            break;
          }
          Entry read = read(in, maxFileBytes, maxTotalBytes - total);
          if (read == null) {
            skipped.add(new Skipped(name, "The archive unpacks to more than " + (maxTotalBytes / 1_048_576) + " MB; stopped here"));
            break;
          }
          total += read.size();
          if (read.data() == null) {
            skipped.add(new Skipped(name, "Larger than " + (maxFileBytes / 1_048_576) + " MB"));
            continue;
          }
          byte[] data = read.data();
          if (data.length >= 4 && data[0] == 'P' && data[1] == 'K') {
            skipped.add(new Skipped(name, "Archives inside the archive are not opened"));
            continue;
          }
          documents++;
          try {
            created.add(invoices.upload(new BytesMultipartFile(name, data), me, InvoiceSource.BATCH, null));
          } catch (ApiException e) {
            skipped.add(new Skipped(name, e.getMessage()));
          }
        }
      }
    } catch (ZipException e) {
      throw ApiException.badRequest("invalid_zip", "The archive is damaged or uses an unsupported format");
    } catch (IOException e) {
      throw ApiException.badRequest("invalid_zip", "The archive could not be read");
    }
    audit.log(me.organizationId(), me.userId(), "BATCH_UPLOADED", AuditService.ORGANIZATION, me.organizationId(),
        Map.of("archive", String.valueOf(zip.getOriginalFilename()), "created", created.size(), "skipped", skipped.size()));
    return new BatchResult(created, skipped);
  }

  /** {@code data} is null when the entry is larger than the per-file limit; {@code size} is what it unpacked to. */
  private record Entry(byte[] data, long size) {}

  /**
   * Reads one entry, keeping at most {@code perFile} bytes but counting everything against the archive's budget;
   * null as soon as the budget is exhausted (a zip bomb stops here instead of unpacking to the end).
   */
  private static Entry read(InputStream in, long perFile, long budget) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[64 * 1024];
    long n = 0;
    int r;
    while ((r = in.read(buf)) > 0) {
      n += r;
      if (n > budget) return null;
      if (n <= perFile) out.write(buf, 0, r);
    }
    return new Entry(n <= perFile ? out.toByteArray() : null, n);
  }

  static boolean safePath(String path) {
    String p = path.replace('\\', '/');
    if (p.startsWith("/") || p.matches("^[A-Za-z]:.*")) return false;
    for (String segment : p.split("/")) if (segment.equals("..")) return false;
    return true;
  }

  static String baseName(String path) {
    String p = path.replace('\\', '/');
    return p.substring(p.lastIndexOf('/') + 1).trim();
  }

  private static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
