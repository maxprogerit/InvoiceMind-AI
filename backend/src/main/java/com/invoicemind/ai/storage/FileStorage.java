package com.invoicemind.ai.storage;

import java.util.UUID;

public interface FileStorage {
  /** Stores the bytes and returns an opaque storage key. The key never contains user-supplied text. */
  String store(UUID organizationId, UUID invoiceId, String extension, byte[] data);

  byte[] read(String key);

  void delete(String key);
}
