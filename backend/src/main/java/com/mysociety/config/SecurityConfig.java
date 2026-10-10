package com.mysociety.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysociety.common.Errors;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecretKeySpec jwtKey(@Value("${app.jwt-secret}") String secret) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || secret.startsWith("replace-"))
      throw new IllegalStateException("JWT_SECRET must contain at least 32 random characters.");
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder encoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder decoder(SecretKeySpec key) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("my-society-api"));
    return decoder;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins}") String origins) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
    if (c.getAllowedOrigins().contains("*"))
      throw new IllegalStateException("Explicit CORS origins required.");
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Society-Id",
            "X-Correlation-Id",
            "ngrok-skip-browser-warning"));
    c.setExposedHeaders(List.of("Content-Disposition", "X-Correlation-Id"));
    c.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, ObjectMapper mapper, org.springframework.core.env.Environment env)
      throws Exception {
    http.csrf(c -> c.disable())
        .cors(c -> {})
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a -> {
              a.requestMatchers(
                      "/api/v1/auth/login", "/api/v1/system/health", "/api/v1/application/config")
                  .permitAll();
              if (Arrays.asList(env.getActiveProfiles()).contains("dev"))
                a.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
              a.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> {})
                    .authenticationEntryPoint(
                        (q, r, e) -> {
                          r.setStatus(401);
                          r.setContentType("application/json");
                          mapper.writeValue(
                              r.getOutputStream(),
                              Errors.body(
                                  401, "Your session has expired. Please sign in.", Map.of()));
                        }))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (q, r, x) -> {
                          r.setStatus(401);
                          r.setContentType("application/json");
                          mapper.writeValue(
                              r.getOutputStream(), Errors.body(401, "Please sign in.", Map.of()));
                        })
                    .accessDeniedHandler(
                        (q, r, x) -> {
                          r.setStatus(403);
                          r.setContentType("application/json");
                          mapper.writeValue(
                              r.getOutputStream(), Errors.body(403, "Access denied.", Map.of()));
                        }))
        .headers(
            h ->
                h.contentSecurityPolicy(
                        c ->
                            c.policyDirectives(
                                "default-src 'none'; script-src 'self'; style-src 'self'"
                                    + " 'unsafe-inline'; img-src 'self' data:; connect-src 'self';"
                                    + " frame-ancestors 'none'"))
                    .frameOptions(f -> f.deny()));
    return http.build();
  }
}
