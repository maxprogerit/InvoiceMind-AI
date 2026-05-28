package com.invoicemind.ai.service;

import com.invoicemind.ai.dto.AuthDtos.AuthResponse;
import com.invoicemind.ai.dto.AuthDtos.LoginRequest;
import com.invoicemind.ai.dto.AuthDtos.RegisterRequest;
import com.invoicemind.ai.entity.UserEntity;
import com.invoicemind.ai.entity.UserRole;
import com.invoicemind.ai.repository.UserRepository;
import com.invoicemind.ai.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthResponse register(RegisterRequest request) {
    UserEntity user = UserEntity.builder()
      .organizationId(1L)
      .email(request.email())
      .fullName(request.fullName())
      .passwordHash(passwordEncoder.encode(request.password()))
      .role(UserRole.ROLE_ADMIN)
      .build();
    userRepository.save(user);
    UserDetails details = loadUserByUsername(user.getEmail());
    return new AuthResponse(jwtService.generateToken(details), user.getEmail(), user.getRole().name());
  }

  public AuthResponse login(LoginRequest request) {
    UserEntity user = userRepository.findByEmail(request.email())
      .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid credentials");
    }
    UserDetails details = loadUserByUsername(user.getEmail());
    return new AuthResponse(jwtService.generateToken(details), user.getEmail(), user.getRole().name());
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    UserEntity user = userRepository.findByEmail(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    return new User(user.getEmail(), user.getPasswordHash(), java.util.List.of(new SimpleGrantedAuthority(user.getRole().name())));
  }
}
