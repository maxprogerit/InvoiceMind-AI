package com.invoicemind.ai.common;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;

/** Stable paging envelope (Spring's Page serialisation is not a supported contract). */
public record PageResponse<T>(@NotNull List<T> items, @NotNull int page, @NotNull int size, @NotNull long totalItems,
                              @NotNull int totalPages) {
  public static <T> PageResponse<T> of(Page<?> page, List<T> items) {
    return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }
}
