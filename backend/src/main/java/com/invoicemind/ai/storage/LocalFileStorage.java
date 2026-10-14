package com.invoicemind.ai.storage;

import com.invoicemind.ai.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Files live under {root}/{orgId}/{invoiceId}.{ext}; every path is verified to stay inside the root. */
@Service
public class LocalFileStorage implements FileStorage {
  private final Path root;

  public LocalFileStorage(AppProperties props) {
    this.root = Path.of(props.storage().root()).toAbsolutePath().normalize();
  }

  @Override
  public String store(UUID organizationId, UUID invoiceId, String extension, byte[] data) {
    String key = organizationId + "/" + invoiceId + "." + extension.replaceAll("[^a-z0-9]", "");
    Path target = resolve(key);
    try {
      Files.createDirectories(target.getParent());
      Path tmp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
      Files.write(tmp, data);
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not store file", e);
    }
    return key;
  }

  @Override
  public byte[] read(String key) {
    try {
      return Files.readAllBytes(resolve(key));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read file", e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not delete file", e);
    }
  }

  private Path resolve(String key) {
    Path p = root.resolve(key).normalize();
    if (!p.startsWith(root)) throw new IllegalArgumentException("Storage key escapes the storage root");
    return p;
  }
}
