package com.invoicemind.ai.firm;

import com.invoicemind.ai.firm.FirmDtos.ClientAccess;
import com.invoicemind.ai.firm.FirmDtos.ClientSummary;
import com.invoicemind.ai.firm.FirmDtos.CreateClientRequest;
import com.invoicemind.ai.firm.FirmDtos.CreateMemberRequest;
import com.invoicemind.ai.firm.FirmDtos.FirmInfo;
import com.invoicemind.ai.firm.FirmDtos.FirmMemberDto;
import com.invoicemind.ai.firm.FirmDtos.FirmTeam;
import com.invoicemind.ai.firm.FirmDtos.GrantAccessRequest;
import com.invoicemind.ai.firm.FirmDtos.UpdateClientRequest;
import com.invoicemind.ai.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The accounting firm's view over its client companies. Membership is checked against the database in the services; a
 * caller outside a firm gets 403 here and nothing else about the endpoints changes for them.
 */
@Tag(name = "Firm")
@RestController
@RequestMapping("/api/v1/firm")
@RequiredArgsConstructor
public class FirmController {
  private final FirmService firm;
  private final FirmTeamService team;

  @GetMapping
  public FirmInfo info() { return firm.info(CurrentUser.get()); }

  @GetMapping("/clients")
  public List<ClientSummary> clients() { return firm.clients(CurrentUser.get()); }

  @PostMapping("/clients")
  @ResponseStatus(HttpStatus.CREATED)
  public ClientSummary createClient(@Valid @RequestBody CreateClientRequest req) {
    return firm.createClient(CurrentUser.get(), req);
  }

  @PutMapping("/clients/{id}")
  public ClientSummary updateClient(@PathVariable UUID id, @Valid @RequestBody UpdateClientRequest req) {
    return firm.updateClient(CurrentUser.get(), id, req);
  }

  @PostMapping("/clients/{id}/deactivate")
  public ClientSummary deactivate(@PathVariable UUID id) { return firm.setActive(CurrentUser.get(), id, false); }

  @PostMapping("/clients/{id}/activate")
  public ClientSummary activate(@PathVariable UUID id) { return firm.setActive(CurrentUser.get(), id, true); }

  @GetMapping("/members")
  public FirmTeam members() { return team.team(CurrentUser.get()); }

  @PostMapping("/members")
  @ResponseStatus(HttpStatus.CREATED)
  public FirmMemberDto addMember(@Valid @RequestBody CreateMemberRequest req) { return team.add(CurrentUser.get(), req); }

  @PutMapping("/members/{userId}/access/{organizationId}")
  public ClientAccess grant(@PathVariable UUID userId, @PathVariable UUID organizationId,
                            @Valid @RequestBody GrantAccessRequest req) {
    return team.grant(CurrentUser.get(), userId, organizationId, req.role());
  }

  @DeleteMapping("/members/{userId}/access/{organizationId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID userId, @PathVariable UUID organizationId) {
    team.revoke(CurrentUser.get(), userId, organizationId);
  }

  @DeleteMapping("/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable UUID userId) { team.remove(CurrentUser.get(), userId); }
}
