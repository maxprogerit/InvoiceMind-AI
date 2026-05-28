package com.invoicemind.ai.controller;

import com.invoicemind.ai.entity.UserEntity;
import com.invoicemind.ai.repository.UserRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {
  private final UserRepository userRepository;

  @GetMapping("/profile")
  public UserEntity profile() { return userRepository.findById(1L).orElseThrow(); }

  @PutMapping("/profile")
  public UserEntity updateProfile(@RequestBody Map<String, String> body) {
    UserEntity user = userRepository.findById(1L).orElseThrow();
    if (body.containsKey("fullName")) user.setFullName(body.get("fullName"));
    if (body.containsKey("email")) user.setEmail(body.get("email"));
    return userRepository.save(user);
  }

  @GetMapping("/api-keys")
  public List<Map<String, Object>> apiKeys() {
    return List.of(
        Map.of("id", 1, "name", "Default API Key", "keyPrefix", "im_pub_",
               "active", true, "createdAt", "2025-01-01"),
        Map.of("id", 2, "name", "Read-Only Key", "keyPrefix", "im_ro_",
               "active", true, "createdAt", "2025-02-01"));
  }

  @GetMapping("/org")
  public Map<String, Object> orgSettings() {
    return Map.of("orgId", 1, "name", "InvoiceMind Demo Org", "plan", "enterprise",
        "ocrEngine", "simulation", "autoApproveThreshold", 500, "approvalRequired", true);
  }

  @PutMapping("/org")
  public Map<String, Object> updateOrg(@RequestBody Map<String, Object> body) {
    return Map.of("updated", true, "settings", body);
  }
}
