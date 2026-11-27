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

  public record VendorDto(@NotNull UUID id, @NotNull String name, String taxId, String defaultCurrency,
                          @NotNull VendorStatus status, String verifiedByName, Instant verifiedAt,
                          @NotNull List<AliasDto> aliases, @NotNull long invoiceCount, @NotNull Instant createdAt) {}

  public record UpdateVendorRequest(@NotBlank @Size(max = 300) String name, @Size(max = 60) String taxId,
                                    @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "must be a 3-letter currency code")
                                    String defaultCurrency) {}

  public record AddAliasRequest(@NotBlank @Size(max = 300) String alias) {}
}
