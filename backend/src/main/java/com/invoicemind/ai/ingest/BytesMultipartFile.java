package com.invoicemind.ai.ingest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.springframework.web.multipart.MultipartFile;

/** An in-memory upload, so mail attachments go through exactly the same service as browser uploads. */
final class BytesMultipartFile implements MultipartFile {
  private final String filename;
  private final byte[] data;

  BytesMultipartFile(String filename, byte[] data) {
    this.filename = filename;
    this.data = data;
  }

  @Override public String getName() { return "file"; }
  @Override public String getOriginalFilename() { return filename; }
  @Override public String getContentType() { return "application/octet-stream"; } // ignored: content is sniffed
  @Override public boolean isEmpty() { return data.length == 0; }
  @Override public long getSize() { return data.length; }
  @Override public byte[] getBytes() { return data; }
  @Override public InputStream getInputStream() { return new ByteArrayInputStream(data); }
  @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), data); }
}
