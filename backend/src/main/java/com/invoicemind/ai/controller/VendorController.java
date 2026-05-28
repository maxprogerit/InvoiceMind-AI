package com.invoicemind.ai.controller;

import com.invoicemind.ai.entity.VendorEntity;
import com.invoicemind.ai.service.VendorService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {
  private final VendorService vendorService;

  @GetMapping
  public List<VendorEntity> list() { return vendorService.listAll(); }

  @GetMapping("/{id}")
  public VendorEntity get(@PathVariable Long id) { return vendorService.getById(id); }

  @PostMapping
  public VendorEntity create(@RequestBody VendorEntity vendor) { return vendorService.create(vendor); }

  @PutMapping("/{id}")
  public VendorEntity update(@PathVariable Long id, @RequestBody VendorEntity vendor) {
    return vendorService.update(id, vendor);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    vendorService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/stats")
  public Map<String, Object> stats(@PathVariable Long id) { return vendorService.getStats(id); }
}
