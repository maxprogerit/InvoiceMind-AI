package com.invoicemind.ai.fx;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FxDtos {
  private FxDtos() {}

  public record OrganizationDto(@NotNull UUID id, @NotNull String name, @NotNull String baseCurrency) {}

  public record UpdateOrganizationRequest(@NotBlank @Size(min = 3, max = 3) @Pattern(regexp = "[A-Za-z]{3}") String baseCurrency) {}

  /** A rate entered by an admin: 1 EUR = {@code perEur} units of {@code currency}, valid from {@code date} until replaced. */
  public record ManualRate(@NotNull long id, @NotNull String currency, @NotNull LocalDate date, @NotNull BigDecimal perEur,
                           @NotNull Instant createdAt) {}

  public record ManualRateRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
      @NotNull LocalDate date,
      @NotNull @DecimalMin(value = "0.00000001") BigDecimal perEur) {}

  /** What the Settings page shows: the base currency, how far the ECB rates reach, and the manual rates. */
  public record FxStatus(@NotNull String baseCurrency, @NotNull boolean ecbEnabled, LocalDate ecbCoveredFrom,
                         LocalDate ecbCoveredTo, String lastError, @NotNull List<ManualRate> manualRates) {}
}
