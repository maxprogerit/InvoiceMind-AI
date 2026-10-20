package com.invoicemind.ai.firm;

import com.invoicemind.ai.user.OrganizationEntity;
import com.invoicemind.ai.user.OrganizationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FirmService {
  private final FirmRepository firms;
  private final FirmMemberRepository members;
  private final OrganizationRepository organizations;

  /** A company that signs up on its own is a firm with one client: itself. Joins the caller's transaction. */
  @Transactional
  public OrganizationEntity createFirmOfOne(String name) {
    FirmEntity firm = firms.save(new FirmEntity(name));
    OrganizationEntity org = new OrganizationEntity(name);
    org.setFirmId(firm.getId());
    return organizations.save(org);
  }

  @Transactional
  public void addMember(UUID firmId, UUID userId, FirmRole role) {
    members.save(new FirmMemberEntity(firmId, userId, role));
  }
}
