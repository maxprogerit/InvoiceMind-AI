package com.invoicemind.ai.storage;

import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.config.AppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload gatekeeper: size limit, content sniffing by magic bytes (the client's Content-Type and
 * extension are ignored), active-content scan for PDFs, and filename sanitisation.
 */
@Component
public class FileValidator {
  public record ValidatedFile(byte[] data, String contentType, String extension, String safeName, String sha256) {}

  private final long maxBytes;

  public FileValidator(AppProperties props) {
    this.maxBytes = props.storage().maxFileBytes();
  }

  public ValidatedFile validate(MultipartFile file) {
    if (file == null || file.isEmpty()) throw ApiException.badRequest("empty_file", "The uploaded file is empty");
    if (file.getSize() > maxBytes) throw tooLarge();
    byte[] data;
    try {
      data = file.getBytes();
    } catch (IOException e) {
      throw ApiException.badRequest("unreadable_upload", "The upload could not be read");
    }
    return validate(data, file.getOriginalFilename());
  }

  public ValidatedFile validate(byte[] data, String originalName) {
    if (data.length == 0) throw ApiException.badRequest("empty_file", "The uploaded file is empty");
    if (data.length > maxBytes) throw tooLarge();

    Type type = sniff(data);
    if (type == null) {
      throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_type",
          "Only PDF, PNG, JPEG, TIFF and WEBP files are accepted");
    }
    if (type == Type.PDF && hasActiveContent(data)) {
      throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "active_content",
          "PDFs containing JavaScript, launch actions or embedded files are not accepted");
    }
    return new ValidatedFile(data, type.mime, type.ext, sanitizeName(originalName, type.ext), sha256(data));
  }

  enum Type {
    PDF("application/pdf", "pdf"), PNG("image/png", "png"), JPEG("image/jpeg", "jpg"),
    TIFF("image/tiff", "tif"), WEBP("image/webp", "webp");

    final String mime;
    final String ext;

    Type(String mime, String ext) {
      this.mime = mime;
      this.ext = ext;
    }
  }

  static Type sniff(byte[] d) {
    if (startsWith(d, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return Type.PDF;
    if (startsWith(d, new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})) return Type.PNG;
    if (startsWith(d, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return Type.JPEG;
    if (startsWith(d, new byte[]{'I', 'I', '*', 0}) || startsWith(d, new byte[]{'M', 'M', 0, '*'})) return Type.TIFF;
    if (d.length >= 12 && startsWith(d, "RIFF".getBytes(StandardCharsets.US_ASCII))
        && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return Type.WEBP;
    return null;
  }

  /**
   * Cheap keyword scan. It cannot see inside compressed object streams, so it is defence in depth,
   * not a guarantee; the PDF is only ever rendered by the user's own browser viewer.
   */
  static boolean hasActiveContent(byte[] data) {
    String s = new String(data, StandardCharsets.ISO_8859_1);
    return s.contains("/JavaScript") || s.contains("/JS ") || s.contains("/JS(") || s.contains("/Launch")
        || s.contains("/EmbeddedFile");
  }

  static String sanitizeName(String original, String ext) {
    String name = original == null ? "" : original;
    name = name.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    name = Normalizer.normalize(name, Normalizer.Form.NFKC);
    int dot = name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    base = base.replaceAll("[^\\p{L}\\p{N} _()\\-]", "_").replaceAll("[ _]{2,}", "_").strip();
    base = base.replaceAll("^[._\\-]+", "");
    if (base.isEmpty()) base = "invoice";
    if (base.length() > 100) base = base.substring(0, 100);
    return base + "." + ext.toLowerCase(Locale.ROOT);
  }

  private static boolean startsWith(byte[] data, byte[] prefix) {
    if (data.length < prefix.length) return false;
    for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
    return true;
  }

  private static ApiException tooLarge() {
    return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large", "File exceeds the 15 MB limit");
  }

  static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
