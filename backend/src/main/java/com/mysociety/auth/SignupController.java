package com.mysociety.auth;

import com.mysociety.common.*;
import com.mysociety.platform.PlatformController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Transactional
public class SignupController {
  private final PlatformController platform;

  public SignupController(PlatformController platform) {
    this.platform = platform;
  }

  public record Signup(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 20) String mobile,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Size(max = 72) String confirmPassword) {}

  @PostMapping("/signup")
  public Map<String, Object> signup(@Valid @RequestBody Signup body) {
    if (!body.password().equals(body.confirmPassword()))
      throw ApiException.bad("Passwords do not match.");
    // No role, society ID or administrator flag is accepted by this public DTO.
    platform.newUser(
        new Input(
            Map.of(
                "name",
                body.name(),
                "email",
                body.email(),
                "mobile",
                body.mobile(),
                "password",
                body.password())));
    return Map.of("message", "Account created successfully. Sign in to request society access.");
  }
}
