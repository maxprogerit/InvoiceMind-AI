package com.invoicemind.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicemind.ai.common.ErrorResponse;
import com.invoicemind.ai.common.RequestIdFilter;
import com.invoicemind.ai.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
  private final JwtAuthFilter jwtAuthFilter;
  private final AppProperties props;
  private final ObjectMapper mapper;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // Stateless bearer-token API: no ambient session cookie to forge requests with.
        // The only cookie (refresh token) is SameSite=Strict, path-scoped, and its endpoints
        // additionally require a custom header that cross-site forms cannot send.
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsSource()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                "/api/v1/auth/logout", "/api/v1/auth/config", "/api/v1/auth/oidc/**").permitAll()
            .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
            .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
            // STOMP authenticates in the CONNECT frame (see WebSocketConfig), not in the HTTP upgrade.
            .requestMatchers("/ws/**").permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(eh -> eh
            .authenticationEntryPoint((req, res, ex) -> write(req, res, 401, "unauthorized", "Authentication required"))
            .accessDeniedHandler((req, res, ex) -> write(req, res, 403, "forbidden",
                "You do not have permission to perform this action")))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  CorsConfigurationSource corsSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(props.cors().allowedOrigins());
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", RequestIdFilter.HEADER));
    cfg.setExposedHeaders(List.of("Content-Disposition", RequestIdFilter.HEADER, "Retry-After"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cfg);
    return source;
  }

  private void write(HttpServletRequest req, HttpServletResponse res, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(res.getOutputStream(), new ErrorResponse(Instant.now(), status, code, message,
        req.getRequestURI(), MDC.get(RequestIdFilter.MDC_KEY), null));
  }
}
