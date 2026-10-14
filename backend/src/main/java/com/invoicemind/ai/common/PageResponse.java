package com.invoicemind.ai.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable paging envelope (Spring's Page serialisation is not a supported contract). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
  public static <T> PageResponse<T> of(Page<?> page, List<T> items) {
    return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }
}
