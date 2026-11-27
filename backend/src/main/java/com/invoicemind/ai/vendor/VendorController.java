package com.invoicemind.ai.vendor;

import com.invoicemind.ai.security.CurrentUser;
import com.invoicemind.ai.vendor.VendorDtos.AddAliasRequest;
import com.invoicemind.ai.vendor.VendorDtos.UpdateVendorRequest;
import com.invoicemind.ai.vendor.VendorDtos.VendorDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "vendors")
@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {
  private final VendorAdminService service;

  @GetMapping
  public List<VendorDto> list(@RequestParam(required = false) VendorStatus status) {
    return service.list(CurrentUser.get(), status);
  }

  @PostMapping("/{id}/verify")
  public VendorDto verify(@PathVariable UUID id) {
    return service.verify(id, CurrentUser.get());
  }

  @PutMapping("/{id}")
  public VendorDto update(@PathVariable UUID id, @Valid @RequestBody UpdateVendorRequest request) {
    return service.update(id, request, CurrentUser.get());
  }

  @PostMapping("/{id}/aliases")
  public VendorDto addAlias(@PathVariable UUID id, @Valid @RequestBody AddAliasRequest request) {
    return service.addAlias(id, request.alias(), CurrentUser.get());
  }

  @DeleteMapping("/{id}/aliases/{aliasId}")
  public VendorDto removeAlias(@PathVariable UUID id, @PathVariable long aliasId) {
    return service.removeAlias(id, aliasId, CurrentUser.get());
  }
}
