package com.invoicemind.ai.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for {@code app.storage.type=s3}: AWS S3 or any S3-compatible store (MinIO, Ceph, R2...).
 * Leave the keys empty on AWS to use the SDK's default credential chain (IAM role, environment...).
 */
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(
    @DefaultValue("invoicemind") String bucket,
    @DefaultValue("us-east-1") String region,
    @DefaultValue("") String endpoint,
    @DefaultValue("") String accessKey,
    @DefaultValue("") String secretKey,
    @DefaultValue("false") boolean pathStyle,
    @DefaultValue("false") boolean createBucket) {}
