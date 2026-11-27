package com.invoicemind.ai.approval;

import com.invoicemind.ai.approval.ApprovalPolicyService.Rules;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "approval-rules")
@RestController
@RequestMapping("/api/v1/approval-rules")
@RequiredArgsConstructor
public class ApprovalPolicyController {
  private final ApprovalPolicyService service;

  @GetMapping
  public Rules list() {
    return service.list(CurrentUser.get());
  }

  @PutMapping
  public Rules replace(@Valid @RequestBody Rules rules) {
    return service.replace(rules, CurrentUser.get());
  }
}
