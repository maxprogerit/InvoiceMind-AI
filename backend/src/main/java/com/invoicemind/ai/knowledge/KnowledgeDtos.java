package com.invoicemind.ai.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class KnowledgeDtos {
  private KnowledgeDtos() {}

  public record DocumentDto(@NotNull UUID id, @NotNull String title, String sourceFilename, @NotNull int charCount,
                            @NotNull int chunkCount, @NotNull String createdByName, @NotNull Instant createdAt) {}

  public record CreateTextRequest(@NotBlank @Size(max = 300) String title,
                                  @NotBlank @Size(min = 20, max = 200_000) String content) {}

  public record AskRequest(@NotBlank @Size(max = 1000) String question) {}

  /** A retrieved passage. {@code n} is the number the answer uses to cite it, e.g. "[2]". */
  public record Source(@NotNull int n, @NotNull UUID documentId, @NotNull String title, @NotNull String excerpt,
                       @NotNull double score) {}

  public record AnswerDto(@NotNull String answer, @NotNull boolean insufficientContext, @NotNull boolean injectionSuspected,
                          @NotNull List<Integer> citations, @NotNull List<Source> sources, String model) {}
}
