package com.invoicemind.ai.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * A real S3-compatible server for the storage tests: SeaweedFS (master + volume + filer + S3 gateway in one
 * container). The access keys are written into the gateway's identity file from the environment at start-up.
 */
public final class S3ServerForTests {
  public static final String ACCESS_KEY = "s3-test-user";
  public static final String SECRET_KEY = "s3-test-secret-0123456789";

  private static final String START = "printf '{\"identities\":[{\"name\":\"it\",\"credentials\":[{\"accessKey\":\"%s\","
      + "\"secretKey\":\"%s\"}],\"actions\":[\"Admin\",\"Read\",\"Write\",\"List\",\"Tagging\"]}]}' "
      + "\"$S3_ACCESS_KEY\" \"$S3_SECRET_KEY\" > /tmp/s3.json; "
      + "exec weed server -dir=/data -s3 -s3.port=8333 -s3.config=/tmp/s3.json -volume.max=50 -master.volumeSizeLimitMB=128";

  private static GenericContainer<?> container;

  private S3ServerForTests() {}

  public static synchronized GenericContainer<?> start() {
    if (container == null) {
      GenericContainer<?> c = new GenericContainer<>("chrislusf/seaweedfs:4.48")
          .withEnv("S3_ACCESS_KEY", ACCESS_KEY)
          .withEnv("S3_SECRET_KEY", SECRET_KEY)
          .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sh", "-c", START))
          .withExposedPorts(8333, 9333)
          // ready once the master can hand out a writable volume (the first PUT will succeed) AND the S3 gateway answers
          .waitingFor(new org.testcontainers.containers.wait.strategy.WaitAllStrategy()
              .withStrategy(Wait.forHttp("/dir/assign").forPort(9333).forResponsePredicate(body -> body.contains("\"fid\"")))
              .withStrategy(Wait.forHttp("/status").forPort(8333)));
      c.start(); // only published once started, so a failed start is reported again instead of half-initialised
      container = c;
    }
    return container;
  }

  public static String endpoint() {
    GenericContainer<?> c = start();
    return "http://" + c.getHost() + ":" + c.getMappedPort(8333);
  }
}
