package com.invoicemind.ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
  public record RegisterRequest(@Email String email, @NotBlank String fullName, @NotBlank String password) {}
  public record LoginRequest(@Email String email, @NotBlank String password) {}
  public record AuthResponse(String token, String email, String role) {}
}
