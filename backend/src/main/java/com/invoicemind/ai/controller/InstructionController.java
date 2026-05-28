package com.invoicemind.ai.controller;

import com.invoicemind.ai.entity.InstructionRuleEntity;
import com.invoicemind.ai.service.InstructionRuleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/instructions")
@RequiredArgsConstructor
public class InstructionController {
  private final InstructionRuleService ruleService;

  @GetMapping
  public List<InstructionRuleEntity> list() { return ruleService.listAll(); }

  @PostMapping
  public InstructionRuleEntity create(@RequestBody InstructionRuleEntity rule) {
    return ruleService.create(rule);
  }

  @PutMapping("/{id}")
  public InstructionRuleEntity update(@PathVariable Long id, @RequestBody InstructionRuleEntity rule) {
    return ruleService.update(id, rule);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    ruleService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
