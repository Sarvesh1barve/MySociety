package com.mysociety.auth;

import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.security.*;
import com.mysociety.user.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Transactional
public class AccountSecurityController {
  private final Access access;
  private final AccountRepository accounts;
  private final PasswordEncoder passwords;
  private final LoginThrottle throttle;
  private final Store db;
  private final AuditService audit;
  private final SecureRandom random = new SecureRandom();
  @PersistenceContext private EntityManager em;

  public AccountSecurityController(
      Access access,
      AccountRepository accounts,
      PasswordEncoder passwords,
      LoginThrottle throttle,
      Store db,
      AuditService audit) {
    this.access = access;
    this.accounts = accounts;
    this.passwords = passwords;
    this.throttle = throttle;
    this.db = db;
    this.audit = audit;
  }

  public record Confirm(@NotBlank @Size(max = 72) String currentPassword) {}

  public record Reset(
      @NotBlank @Size(max = 254) String login,
      @NotBlank @Size(max = 80) String recoveryCode,
      @NotBlank @Size(min = 12, max = 72) String newPassword) {}

  public record LoginDetails(
      @Size(max = 254) String email,
      @Size(max = 20) String mobile,
      @NotBlank @Size(max = 72) String currentPassword) {}

  public record AdminReset(
      @NotBlank @Size(max = 72) String currentPassword,
      @NotBlank @Size(min = 12, max = 72) String newPassword) {}

  private void confirm(Account account, String password) {
    String key = "confirm:" + account.id;
    throttle.check(key);
    if (password == null
        || password.getBytes(StandardCharsets.UTF_8).length > 72
        || !passwords.matches(password, account.passwordHash)) {
      throttle.failed(key);
      throw ApiException.bad("Current password is incorrect.");
    }
    throttle.success(key);
  }

  @GetMapping("recovery-codes")
  public Map<String, Object> status() {
    long count =
        em.createQuery(
                "select count(r) from RecoveryCode r where r.userId=:u and r.usedAt is null",
                Long.class)
            .setParameter("u", access.user().id)
            .getSingleResult();
    return Map.of("remaining", count);
  }

  @PostMapping("recovery-codes")
  public Map<String, Object> generate(@Valid @RequestBody Confirm body) {
    Account account = accounts.lockById(access.user().id).orElseThrow(ApiException::missing);
    confirm(account, body.currentPassword());
    em.createQuery("delete from RecoveryCode r where r.userId=:u")
        .setParameter("u", account.id)
        .executeUpdate();
    List<String> codes = new ArrayList<>();
    for (int n = 0; n < 5; n++) {
      byte[] bytes = new byte[16];
      random.nextBytes(bytes);
      String raw = HexFormat.of().withUpperCase().formatHex(bytes);
      String printable =
          String.join(
              "-",
              raw.substring(0, 8),
              raw.substring(8, 16),
              raw.substring(16, 24),
              raw.substring(24));
      RecoveryCode code = new RecoveryCode();
      code.userId = account.id;
      code.codeHash = hash(account.id, raw);
      db.save(code);
      codes.add(printable);
    }
    audit.log(null, "RECOVERY_CODES_REGENERATED", account, null);
    return Map.of("codes", codes, "remaining", codes.size());
  }

  @PostMapping("forgot-password")
  public Map<String, String> recover(@Valid @RequestBody Reset body) {
    String login = Input.login(body.login());
    String key = "recovery:" + login;
    throttle.check(key);
    if (body.newPassword().getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.bad("Password must fit within 72 UTF-8 bytes.");
    Account account = accounts.lockByLogin(login).orElse(null);
    String normalized =
        body.recoveryCode().replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
    // Query the same digest shape for unknown identities; never return account-existence details.
    UUID userId = account == null ? new UUID(0, 0) : account.id;
    var codes =
        db.query(
            RecoveryCode.class,
            "userId=:u and codeHash=:h and usedAt is null",
            Map.of("u", userId, "h", hash(userId, normalized)));
    if (account == null || !"ACTIVE".equals(account.status) || codes.isEmpty()) {
      throttle.failed(key);
      throw ApiException.bad(
          "The login or recovery code is invalid, already used, or unavailable.");
    }
    codes.getFirst().usedAt = Instant.now();
    account.passwordHash = passwords.encode(body.newPassword());
    account.tokenVersion++;
    throttle.success(key);
    // Recovery has no authenticated session, but possession of the code identifies the actor.
    AuditLog event = new AuditLog();
    event.actorUserId = account.id;
    event.action = "PASSWORD_RECOVERED";
    event.entityType = "Account";
    event.entityId = account.id;
    event.correlationId = org.slf4j.MDC.get("correlationId");
    db.save(event);
    return Map.of(
        "message",
        "Password reset. Sign in with your new password. This recovery code cannot be used again.");
  }

  @PutMapping("login-details")
  public Map<String, String> loginDetails(@Valid @RequestBody LoginDetails body) {
    Account account = accounts.lockById(access.user().id).orElseThrow(ApiException::missing);
    Map<String, Object> fields = new HashMap<>();
    fields.put("email", body.email());
    fields.put("mobile", body.mobile());
    Input input = new Input(fields);
    confirm(account, body.currentPassword());
    String email = input.email("email");
    String mobile = input.mobile("mobile", false);
    if (email == null && mobile == null)
      throw ApiException.bad("Keep at least one email or mobile login.");
    String before = audit.snapshot(account);
    account.email = email;
    account.mobile = mobile;
    // Record the authenticated actor before invalidating this request's token revision.
    audit.log(null, "LOGIN_DETAILS_CHANGED", account, before);
    account.tokenVersion++;
    db.flush();
    return Map.of(
        "message", "Login details updated. Sign in using your new email or mobile number.");
  }

  private static String hash(UUID userId, String code) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((userId + ":" + code).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @PostMapping("admin-reset/{id}")
  public Map<String, String> adminReset(
      @PathVariable UUID id, @Valid @RequestBody AdminReset body) {
    access.platform();
    confirm(access.user(), body.currentPassword());
    Account target = accounts.lockById(id).orElseThrow(ApiException::missing);
    if (target.superAdmin)
      throw ApiException.bad(
          "Platform owners must use their own recovery codes or change password from their"
              + " profile.");
    if (body.newPassword().getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.bad("Password must fit within 72 UTF-8 bytes.");
    target.passwordHash = passwords.encode(body.newPassword());
    target.tokenVersion++;
    em.createQuery("delete from RecoveryCode r where r.userId=:u")
        .setParameter("u", target.id)
        .executeUpdate();
    audit.log(null, "PASSWORD_RESET_BY_PLATFORM_OWNER", target, null);
    return Map.of(
        "message",
        "Password reset and old sessions/recovery codes revoked. Share the temporary password"
            + " securely after verifying identity.");
  }
}
