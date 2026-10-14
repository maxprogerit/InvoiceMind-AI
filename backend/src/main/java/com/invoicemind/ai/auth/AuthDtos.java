package com.invoicemind.ai.auth;

import com.invoicemind.ai.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

  public record UserDto(UUID id, String email, String fullName, Role role, UUID organizationId, String organizationName) {}

  public record AuthResponse(String accessToken, long expiresIn, UserDto user) {}
}
