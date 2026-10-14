package com.invoicemind.ai.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.config.AppProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FileValidatorTest {
  final FileValidator validator = new FileValidator(new AppProperties(null, null, null,
      new AppProperties.Storage("./storage", 1024), null, null, null, null, null));

  static byte[] bytes(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }

  @Test
  void acceptsRealFileTypesRegardlessOfClaimedName() {
    var pdf = validator.validate(bytes("%PDF-1.7\n%%EOF"), "invoice.exe");
    assertThat(pdf.contentType()).isEqualTo("application/pdf");
    assertThat(pdf.safeName()).isEqualTo("invoice.pdf"); // extension comes from the content, not the client

    var png = validator.validate(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0}, "scan.pdf");
    assertThat(png.contentType()).isEqualTo("image/png");
    assertThat(png.safeName()).isEqualTo("scan.png");

    assertThat(validator.validate(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0}, "a.jpeg").extension())
        .isEqualTo("jpg");
    assertThat(validator.validate(bytes("RIFF\0\0\0\0WEBPVP8 "), "a.webp").contentType()).isEqualTo("image/webp");
    assertThat(validator.validate(new byte[]{'I', 'I', '*', 0, 8, 0}, "a.tiff").contentType()).isEqualTo("image/tiff");
  }

  @Test
  void rejectsExecutablesAndMarkupDisguisedAsPdf() {
    for (String payload : new String[]{"MZ\u0090\u0000 PE exe", "<html><script>alert(1)</script>", "#!/bin/sh\nrm -rf /",
        "PK\u0003\u0004 zip", "GIF89a"}) {
      assertThatThrownBy(() -> validator.validate(bytes(payload), "invoice.pdf"))
          .isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            assertThat(e.code()).isEqualTo("unsupported_type");
          });
    }
  }

  @Test
  void rejectsPdfsWithActiveContent() {
    for (String keyword : new String[]{"/JavaScript", "/JS (app.alert(1))", "/Launch", "/EmbeddedFile"}) {
      assertThatThrownBy(() -> validator.validate(bytes("%PDF-1.7\n1 0 obj<< " + keyword + " >>endobj"), "x.pdf"))
          .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("active_content"));
    }
  }

  @Test
  void enforcesSizeAndEmptiness() {
    assertThatThrownBy(() -> validator.validate(new byte[0], "x.pdf"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("empty_file"));
    byte[] big = new byte[1025];
    System.arraycopy(bytes("%PDF-"), 0, big, 0, 5);
    assertThatThrownBy(() -> validator.validate(big, "x.pdf"))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
  }

  @Test
  void sanitisesFilenames() {
    assertThat(FileValidator.sanitizeName("../../etc/passwd", "pdf")).isEqualTo("passwd.pdf");
    assertThat(FileValidator.sanitizeName("C:\\Users\\x\\evil.exe", "pdf")).isEqualTo("evil.pdf");
    assertThat(FileValidator.sanitizeName("inv\u0000oice\r\n<script>.pdf", "pdf")).doesNotContain("\u0000", "<", ">", "\n");
    assertThat(FileValidator.sanitizeName("   ", "png")).isEqualTo("invoice.png");
    assertThat(FileValidator.sanitizeName(null, "pdf")).isEqualTo("invoice.pdf");
    assertThat(FileValidator.sanitizeName(".hidden.pdf", "pdf")).isEqualTo("hidden.pdf");
    assertThat(FileValidator.sanitizeName("Rechnung Müller (März).pdf", "pdf")).isEqualTo("Rechnung Müller (März).pdf");
    assertThat(FileValidator.sanitizeName("a".repeat(500) + ".pdf", "pdf").length()).isLessThanOrEqualTo(104);
  }
}
