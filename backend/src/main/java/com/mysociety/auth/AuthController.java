package com.mysociety.auth;

import com.mysociety.common.*;
import com.mysociety.membership.Membership;
import com.mysociety.security.*;
import com.mysociety.society.Society;
import com.mysociety.user.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Transactional
public class AuthController {
  private final Store db;
  private final Access access;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final String dummy;
  private final com.mysociety.user.AccountRepository accounts;
  private final LoginThrottle throttle;
  private final SessionTokens sessions;

  public AuthController(
      Store db,
      Access access,
      PasswordEncoder passwords,
      JwtEncoder encoder,
      com.mysociety.user.AccountRepository accounts,
      LoginThrottle throttle,
      SessionTokens sessions) {
    this.db = db;
    this.access = access;
    this.passwords = passwords;
    this.encoder = encoder;
    this.accounts = accounts;
    this.throttle = throttle;
    this.sessions = sessions;
    dummy = passwords.encode(UUID.randomUUID().toString());
  }

  public record Login(
      @NotBlank @Size(max = 254) String login, @NotBlank @Size(max = 72) String password) {}

  @PostMapping("/auth/login")
  public Map<String, Object> login(
      @Valid @RequestBody Login body, jakarta.servlet.http.HttpServletResponse response) {
    String identifier = Input.login(body.login());
    throttle.check(identifier);
    Account a = accounts.findByLogin(identifier).orElse(null);
    boolean valid =
        body.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
            && passwords.matches(body.password(), a == null ? dummy : a.passwordHash);
    if (!valid || a == null || !"ACTIVE".equals(a.status)) {
      throttle.failed(identifier);
      throw new ApiException(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Incorrect email/mobile or password.");
    }
    throttle.success(identifier);
    return sessions.create(a, response);
  }

  @GetMapping("/auth/me")
  public Map<String, Object> me() {
    return Views.of(access.user());
  }

  public record Profile(@NotBlank @Size(max = 120) String name) {}

  @PatchMapping("/auth/me")
  public Map<String, Object> profile(@Valid @RequestBody Profile p) {
    Account a = access.user();
    a.name = p.name().trim();
    return Views.of(a);
  }

  public record ChangePassword(
      @NotBlank String currentPassword, @Size(min = 12, max = 72) String newPassword) {}

  @PostMapping("/auth/change-password")
  public Map<String, String> password(@Valid @RequestBody ChangePassword b) {
    Account a = access.user();
    if (b.newPassword() == null
        || b.currentPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        || !passwords.matches(b.currentPassword(), a.passwordHash))
      throw ApiException.bad("Current password is incorrect.");
    if (b.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw ApiException.bad("Password must fit within 72 UTF-8 bytes.");
    a.passwordHash = passwords.encode(b.newPassword());
    a.tokenVersion++;
    return Map.of("message", "Password updated. Please sign in again.");
  }

  @GetMapping("/memberships")
  public List<Map<String, Object>> memberships() {
    UUID id = access.user().id;
    return db.query(Membership.class, "userId=:u and status='ACTIVE'", Map.of("u", id)).stream()
        .map(
            m -> {
              var v = Views.of(m);
              v.put("society", Views.of(db.get(Society.class, m.societyId)));
              return v;
            })
        .toList();
  }

  @PostMapping("/memberships/{id}/activate-context")
  public Map<String, Object> activate(@PathVariable UUID id) {
    Membership m = db.get(Membership.class, id);
    if (!m.userId.equals(access.user().id)) throw ApiException.forbidden();
    return context(m.societyId);
  }

  @GetMapping("/context")
  public Map<String, Object> context(@RequestHeader("X-Society-Id") UUID society) {
    Set<String> permissions = access.permissions(society);
    var m =
        db.query(
            Membership.class,
            "societyId=:s and userId=:u and status='ACTIVE'",
            Map.of("s", society, "u", access.user().id));
    return Map.of(
        "society",
        Views.of(db.get(Society.class, society)),
        "permissions",
        permissions,
        "roles",
        m.isEmpty() ? List.of("SUPER_ADMIN") : Permissions.split(m.getFirst().roles),
        "flats",
        access.flats(society));
  }
}
