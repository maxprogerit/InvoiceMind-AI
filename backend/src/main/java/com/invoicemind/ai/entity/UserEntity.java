package com.invoicemind.ai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false) private Long organizationId;
  @Column(nullable = false, unique = true) private String email;
  @Column(nullable = false) private String passwordHash;
  @Column(nullable = false) private String fullName;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private UserRole role;
}
