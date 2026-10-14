package com.invoicemind.ai.storage;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Object storage with the same key layout as the local store: {orgId}/{invoiceId}.{ext}. */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "s3")
public class S3FileStorage implements FileStorage {
  private final S3Client s3;
  private final String bucket;
  private final boolean createBucket;

  public S3FileStorage(S3Properties props) {
    this.bucket = props.bucket();
    this.createBucket = props.createBucket();
    var builder = S3Client.builder()
        .httpClient(UrlConnectionHttpClient.create())
        .region(Region.of(props.region()))
        .credentialsProvider(credentials(props))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(props.pathStyle()).build());
    if (props.endpoint() != null && !props.endpoint().isBlank()) builder.endpointOverride(URI.create(props.endpoint()));
    this.s3 = builder.build();
  }

  private static AwsCredentialsProvider credentials(S3Properties p) {
    if (p.accessKey() == null || p.accessKey().isBlank()) return DefaultCredentialsProvider.create();
    return StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(), p.secretKey()));
  }

  /** Fails fast at startup when the bucket is unreachable instead of at the first upload. */
  @PostConstruct
  void checkBucket() {
    try {
      s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    } catch (NoSuchBucketException e) {
      if (!createBucket) {
        throw new IllegalStateException("S3 bucket '" + bucket + "' does not exist (set S3_CREATE_BUCKET=true to create it)", e);
      }
      s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
      log.info("Created S3 bucket {}", bucket);
    } catch (S3Exception e) {
      if (e.statusCode() == 404 && createBucket) {
        s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        log.info("Created S3 bucket {}", bucket);
      } else {
        throw new IllegalStateException("S3 bucket '" + bucket + "' is not usable: " + e.getMessage(), e);
      }
    }
    log.info("Invoice files are stored in S3 bucket {}", bucket);
  }

  @Override
  public String store(UUID organizationId, UUID invoiceId, String extension, byte[] data) {
    String key = organizationId + "/" + invoiceId + "." + extension.replaceAll("[^a-z0-9]", "");
    try {
      s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(data));
    } catch (RuntimeException e) {
      throw new UncheckedIOException(new IOException("Could not store file: " + e.getMessage(), e));
    }
    return key;
  }

  @Override
  public byte[] read(String key) {
    try {
      return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    } catch (NoSuchKeyException e) {
      throw new UncheckedIOException(new IOException("File not found: " + key, e));
    } catch (RuntimeException e) {
      throw new UncheckedIOException(new IOException("Could not read file: " + e.getMessage(), e));
    }
  }

  @Override
  public void delete(String key) {
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); // idempotent in S3
    } catch (RuntimeException e) {
      throw new UncheckedIOException(new IOException("Could not delete file: " + e.getMessage(), e));
    }
  }
}
