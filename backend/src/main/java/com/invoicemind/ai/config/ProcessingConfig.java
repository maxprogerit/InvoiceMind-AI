package com.invoicemind.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ProcessingConfig {
  /**
   * The workers that run the invoice pipeline (OCR, extraction, validation). Three threads, a bounded queue, and the
   * tracing context is carried over so one upload stays one trace. A task that is rejected (queue full) simply leaves
   * its invoice UPLOADED, where the sweeper picks it up again.
   */
  @Bean(name = "processingExecutor")
  ThreadPoolTaskExecutor processingExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(3);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("invoice-proc-");
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
  }
}
