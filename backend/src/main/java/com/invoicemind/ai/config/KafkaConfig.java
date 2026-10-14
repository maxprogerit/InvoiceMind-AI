package com.invoicemind.ai.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {
  private final AppProperties props;

  @Bean
  NewTopic invoiceUploadedTopic() {
    return new NewTopic(props.processing().topic(), 3, (short) 1);
  }

  @Bean
  NewTopic invoiceUploadedDeadLetterTopic() {
    return new NewTopic(props.processing().topic() + ".DLT", 1, (short) 1);
  }

  /** Infrastructure failures are retried twice (2 s apart), then the record goes to the dead-letter topic. */
  @Bean
  CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template,
        (record, ex) -> new TopicPartition(record.topic() + ".DLT", 0));
    return new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 2));
  }
}
