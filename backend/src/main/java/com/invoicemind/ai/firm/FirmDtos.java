package com.invoicemind.ai.firm;

import com.invoicemind.ai.auth.AuthDtos;
import com.invoicemind.ai.user.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class FirmDtos {
  private FirmDtos() {}

  /** The caller's firm. {@code documentLimit} is null when the firm has no monthly limit. */
  public record FirmInfo(@NotNull UUID id, @NotNull String name, @NotNull String plan, Integer documentLimit,
                         @NotNull long documentsThisMonth, @NotNull FirmRole firmRole, @NotNull int clientCount) {}

  /**
   * One client company with a summary of its documents. {@code monthToDateTotal} is the approved spend of the current
   * month in the client's base currency, counting only invoices already in that currency;
   * {@code monthToDateOtherCurrency} says how many approved invoices of the month are in other currencies and therefore
   * not in the total (nothing is converted behind the reader's back).
   */
  public record ClientSummary(@NotNull UUID id, @NotNull String name, @NotNull String baseCurrency, @NotNull boolean active,
                              @NotNull long documents, @NotNull long inReview, @NotNull long failed,
                              @NotNull long awaitingApproval, @NotNull BigDecimal monthToDateTotal,
                              @NotNull long monthToDateOtherCurrency) {}

  public record CreateClientRequest(
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "Use a three-letter ISO 4217 currency code") String baseCurrency) {}

  public record UpdateClientRequest(
      @Size(min = 1, max = 200) String name,
      @Pattern(regexp = "[A-Za-z]{3}", message = "Use a three-letter ISO 4217 currency code") String baseCurrency) {}

  public record ClientRef(@NotNull UUID id, @NotNull String name, @NotNull boolean active) {}

  public record ClientAccess(@NotNull UUID organizationId, @NotNull Role role) {}

  public record FirmMemberDto(@NotNull UUID userId, @NotNull String email, @NotNull String fullName,
                              @NotNull FirmRole firmRole, @NotNull boolean enabled, @NotNull UUID homeOrganizationId,
                              @NotNull List<ClientAccess> access) {}

  /** The access matrix: who may work in which client, with which role there. */
  public record FirmTeam(@NotNull List<ClientRef> clients, @NotNull List<FirmMemberDto> members) {}

  /** The first entry of {@code access} is the person's home company, the one a sign-in lands in. */
  public record CreateMemberRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 200) String fullName,
      @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_RULE, message = AuthDtos.PASSWORD_MESSAGE) String password,
      @NotNull FirmRole firmRole,
      @NotEmpty @Valid List<ClientAccess> access) {}

  public record GrantAccessRequest(@NotNull Role role) {}
}
