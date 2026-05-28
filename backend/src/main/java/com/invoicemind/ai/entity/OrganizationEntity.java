package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "organizations")
@Getter
@Setter
public class OrganizationEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String name;
  private String plan;
  private String timezone;
}
