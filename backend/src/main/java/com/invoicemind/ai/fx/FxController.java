package com.invoicemind.ai.fx;

import com.invoicemind.ai.fx.FxDtos.FxStatus;
import com.invoicemind.ai.fx.FxDtos.ManualRate;
import com.invoicemind.ai.fx.FxDtos.ManualRateRequest;
import com.invoicemind.ai.fx.FxDtos.OrganizationDto;
import com.invoicemind.ai.fx.FxDtos.UpdateOrganizationRequest;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.security.CurrentUser;
import com.invoicemind.ai.user.OrganizationRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Organisation settings related to money: the reporting currency and the exchange rates behind it. */
@Tag(name = "Settings")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FxController {
  private final ExchangeRateService rates;
  private final OrganizationRepository organizations;

  @GetMapping("/organization")
  public OrganizationDto organization() {
    AuthPrincipal me = CurrentUser.get();
    var org = organizations.findById(me.organizationId()).orElseThrow();
    return new OrganizationDto(org.getId(), org.getName(), org.getBaseCurrency());
  }

  @PutMapping("/organization")
  @PreAuthorize("hasRole('ADMIN')")
  public OrganizationDto updateOrganization(@Valid @RequestBody UpdateOrganizationRequest req) {
    AuthPrincipal me = CurrentUser.get();
    String code = rates.setBaseCurrency(me, req.baseCurrency());
    var org = organizations.findById(me.organizationId()).orElseThrow();
    return new OrganizationDto(org.getId(), org.getName(), code);
  }

  @GetMapping("/fx")
  @PreAuthorize("hasRole('ADMIN')")
  public FxStatus status() {
    return rates.status(CurrentUser.get().organizationId());
  }

  @PutMapping("/fx/manual")
  @PreAuthorize("hasRole('ADMIN')")
  public ManualRate setManual(@Valid @RequestBody ManualRateRequest req) {
    return rates.upsertManual(CurrentUser.get(), req);
  }

  @DeleteMapping("/fx/manual/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteManual(@PathVariable long id) {
    rates.deleteManual(CurrentUser.get(), id);
  }
}
