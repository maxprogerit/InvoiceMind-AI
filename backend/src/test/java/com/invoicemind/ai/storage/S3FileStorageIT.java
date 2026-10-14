package com.invoicemind.ai.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invoicemind.ai.support.S3ServerForTests;
import java.io.UncheckedIOException;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The S3 store against a real S3-compatible server (SeaweedFS in Docker). */
class S3FileStorageIT {

  private static S3FileStorage storage(String bucket, boolean create) {
    S3FileStorage s = new S3FileStorage(new S3Properties(bucket, "us-east-1", S3ServerForTests.endpoint(),
        S3ServerForTests.ACCESS_KEY, S3ServerForTests.SECRET_KEY, true, create));
    s.checkBucket();
    return s;
  }

  @Test
  void storesReadsAndDeletesBinaryContent() {
    S3FileStorage s = storage("it-roundtrip", true);
    UUID org = UUID.randomUUID(), invoice = UUID.randomUUID();
    byte[] data = new byte[300_000];
    new Random(7).nextBytes(data);

    String key = s.store(org, invoice, "pdf", data);
    assertThat(key).isEqualTo(org + "/" + invoice + ".pdf");
    assertThat(s.read(key)).isEqualTo(data);

    byte[] replaced = "second version".getBytes();
    assertThat(s.store(org, invoice, "pdf", replaced)).isEqualTo(key); // same key: overwritten
    assertThat(s.read(key)).isEqualTo(replaced);

    s.delete(key);
    assertThatThrownBy(() -> s.read(key)).isInstanceOf(UncheckedIOException.class).hasMessageContaining("not found");
    s.delete(key); // deleting twice is harmless
  }

  @Test
  void keysNeverContainUserSuppliedText() {
    S3FileStorage s = storage("it-keys", true);
    String key = s.store(UUID.randomUUID(), UUID.randomUUID(), "../../etc/PNG;rm", new byte[]{1});
    assertThat(key).matches("[0-9a-f-]{36}/[0-9a-f-]{36}[.][a-z0-9]*");
    assertThat(key).doesNotContain("..").doesNotContain(";");
  }

  @Test
  void aLargeFileSurvives() {
    S3FileStorage s = storage("it-large", true);
    byte[] data = new byte[15 * 1024 * 1024];
    new Random(11).nextBytes(data);
    String key = s.store(UUID.randomUUID(), UUID.randomUUID(), "png", data);
    assertThat(s.read(key)).isEqualTo(data);
  }

  @Test
  void aMissingBucketFailsAtStartupUnlessCreationIsAllowed() {
    assertThatThrownBy(() -> storage("it-does-not-exist", false))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("it-does-not-exist");
    storage("it-does-not-exist", true); // created on demand
    storage("it-does-not-exist", false); // and now it exists
  }

  @Test
  void wrongCredentialsFailAtStartup() {
    S3FileStorage s = new S3FileStorage(new S3Properties("it-roundtrip", "us-east-1", S3ServerForTests.endpoint(),
        S3ServerForTests.ACCESS_KEY, "wrong-secret", true, false));
    assertThatThrownBy(s::checkBucket).isInstanceOf(IllegalStateException.class);
  }
}
