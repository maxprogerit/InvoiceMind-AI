package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "vendors")
@Getter
@Setter
public class VendorEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private Long organizationId;
  private String name;
  private String contactEmail;
  private String paymentTerms;
  private BigDecimal riskScore;
  private String country;
  private String contactPhone;
  private String address;
}
