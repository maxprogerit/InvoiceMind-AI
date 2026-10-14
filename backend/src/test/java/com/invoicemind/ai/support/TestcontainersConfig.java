package com.invoicemind.ai.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/** Real Postgres, Kafka and Redis; the integration tests exercise the same infrastructure as production. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
  }

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer("apache/kafka:3.8.0");
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redis() {
    return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
  }
}
