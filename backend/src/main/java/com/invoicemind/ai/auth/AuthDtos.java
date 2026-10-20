package com.invoicemind.ai.auth;

import com.invoicemind.ai.firm.FirmRole;
import com.invoicemind.ai.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {
  private AuthDtos() {}

  public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{10,128}$";
  public static final String PASSWORD_MESSAGE = "Password must be 10-128 characters and contain a letter and a digit";

  public record RegisterRequest(
      @NotBlank @Size(max = 200) String organizationName,
      @NotBlank @Size(max = 200) String fullName,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password) {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 128) String password) {}

  /** {@code organizationId} / {@code role} are the active client company and the role there; the firm fields are null outside a firm. */
  public record UserDto(@NotNull UUID id, @NotNull String email, @NotNull String fullName, @NotNull Role role,
                        @NotNull UUID organizationId, @NotNull String organizationName,
                        UUID firmId, FirmRole firmRole) {}

  public record SwitchOrganizationRequest(@NotNull UUID organizationId) {}

  public record OrganizationOption(@NotNull UUID id, @NotNull String name, @NotNull Role role, @NotNull boolean home,
                                   @NotNull boolean active) {}

  public record AuthResponse(@NotNull String accessToken, @NotNull long expiresIn, @NotNull UserDto user) {}
}
