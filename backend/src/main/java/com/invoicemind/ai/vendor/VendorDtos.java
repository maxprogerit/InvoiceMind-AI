package com.invoicemind.ai.vendor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VendorDtos {
  private VendorDtos() {}

  public record AliasDto(@NotNull Long id, @NotNull String alias) {}

  /** A learned default (vendor memory). {@code whenExtracted}: the value on the document it replaces (null: none). */
  public record MemoryDto(@NotNull Long id, @NotNull String field, String whenExtracted, @NotNull String value,
                          String learnedByName, @NotNull Instant learnedAt, @NotNull int timesApplied,
                          UUID learnedFromInvoice) {}

  public record VendorDto(@NotNull UUID id, @NotNull String name, String taxId, String defaultCurrency,
                          Integer paymentTermsDays, String defaultCategory,
                          @NotNull VendorStatus status, String verifiedByName, Instant verifiedAt,
                          @NotNull List<AliasDto> aliases, @NotNull List<MemoryDto> memory,
                          @NotNull long invoiceCount, @NotNull Instant createdAt) {}

  public record UpdateVendorRequest(@NotBlank @Size(max = 300) String name, @Size(max = 60) String taxId,
                                    @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "must be a 3-letter currency code")
                                    String defaultCurrency) {}

  public record AddAliasRequest(@NotBlank @Size(max = 300) String alias) {}
}
