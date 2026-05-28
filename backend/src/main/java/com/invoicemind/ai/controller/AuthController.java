package com.invoicemind.ai.controller;

import com.invoicemind.ai.dto.AuthDtos.AuthResponse;
import com.invoicemind.ai.dto.AuthDtos.LoginRequest;
import com.invoicemind.ai.dto.AuthDtos.RegisterRequest;
import com.invoicemind.ai.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;

  @PostMapping("/register")
  public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return authService.register(request); }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }
}
