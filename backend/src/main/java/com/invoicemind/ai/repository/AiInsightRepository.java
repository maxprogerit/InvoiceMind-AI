package com.invoicemind.ai.repository;

import com.invoicemind.ai.entity.AiInsightEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiInsightRepository extends JpaRepository<AiInsightEntity, Long> {
  List<AiInsightEntity> findTop10ByOrderByCreatedAtDesc();
}
