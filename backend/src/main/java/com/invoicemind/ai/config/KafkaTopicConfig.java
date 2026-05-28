package com.invoicemind.ai.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
  @Bean
  public NewTopic workflowEventsTopic() {
    return TopicBuilder.name("workflow-events").partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic ocrEventsTopic() {
    return TopicBuilder.name("ocr-events").partitions(3).replicas(1).build();
  }
}
