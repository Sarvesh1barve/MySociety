package com.mysociety.auth;

import com.mysociety.common.*;
import com.mysociety.user.Account;
import jakarta.persistence.*;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionTokens {
  public static final String COOKIE = "__Host-ms-session";
  @PersistenceContext private EntityManager em;
  private final JwtEncoder encoder;
  private final SecretKeySpec key;
  private final Set<String> origins;
  private final SecureRandom random = new SecureRandom();

  public SessionTokens(
      JwtEncoder encoder, SecretKeySpec key, @Value("${app.cors-origins}") String origins) {
    this.encoder = encoder;
    this.key = key;
    this.origins = new HashSet<>(Arrays.stream(origins.split(",")).map(String::trim).toList());
  }

  public void browserRequest(HttpServletRequest request) {
    if (!origins.contains(request.getHeader("Origin"))
        || !"1".equals(request.getHeader("X-MS-Session"))) throw ApiException.forbidden();
  }

  @Transactional
  public Map<String, Object> create(Account account, HttpServletResponse response) {
    // Retain only unexpired sessions and their replay history.
    em.createQuery("delete from LoginSession s where s.expiresAt < :now")
        .setParameter("now", Instant.now())
        .executeUpdate();
    var session = new LoginSession();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    session.userId = account.id;
    session.tokenVersion = account.tokenVersion;
    session.tokenHash = hash(raw);
    session.rotatedAt = Instant.now().minusSeconds(61);
    session.expiresAt = Instant.now().plus(Duration.ofDays(30));
    session.idleExpiresAt = Instant.now().plus(Duration.ofDays(7));
    em.persist(session);
    cookie(response, session.id + "." + raw, session.expiresAt);
    return accessToken(account, session);
  }

  public String readCookie(HttpServletRequest request) {
    if (request.getCookies() != null)
      for (var cookie : request.getCookies())
        if (COOKIE.equals(cookie.getName())) return cookie.getValue();
    return "";
  }

  private LoginSession find(String value) {
    try {
      return em.find(
          LoginSession.class,
          UUID.fromString(value.split("\\.")[0]),
          LockModeType.PESSIMISTIC_WRITE);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Transactional(noRollbackFor = ApiException.class)
  public Map<String, Object> refresh(String value, HttpServletResponse response) {
    var s = find(value);
    Instant now = Instant.now();
    if (s == null || s.revoked || !s.expiresAt.isAfter(now) || !s.idleExpiresAt.isAfter(now))
      throw invalid(response);
    var account = em.find(Account.class, s.userId);
    if (account == null
        || !"ACTIVE".equals(account.status)
        || s.tokenVersion != account.tokenVersion) {
      s.revoked = true;
      throw invalid(response);
    }
    String raw = value.substring(value.indexOf('.') + 1), digest = hash(raw);
    if (digest.equals(s.tokenHash)) {
      // Short coalescing window handles parallel tabs and lost HTTP responses without false replay.
      if (s.rotatedAt.plusSeconds(60).isBefore(now)) {
        em.createNativeQuery(
                "insert into used_session_tokens(token_hash,session_id) values (:h,:s)")
            .setParameter("h", digest)
            .setParameter("s", s.id)
            .executeUpdate();
        s.previousHash = digest;
        raw = successor(raw);
        s.tokenHash = hash(raw);
        s.rotatedAt = now;
      }
    } else if (digest.equals(s.previousHash) && s.rotatedAt.plusSeconds(60).isAfter(now)) {
      raw = successor(raw);
    } else {
      Number reused =
          (Number)
              em.createNativeQuery(
                      "select count(*) from used_session_tokens where session_id=:s and"
                          + " token_hash=:h")
                  .setParameter("s", s.id)
                  .setParameter("h", digest)
                  .getSingleResult();
      if (reused.longValue() > 0) s.revoked = true;
      throw invalid(response);
    }
    s.idleExpiresAt = now.plus(Duration.ofDays(7));
    cookie(response, s.id + "." + raw, s.expiresAt);
    return accessToken(account, s);
  }

  @Transactional
  public void logout(String value, HttpServletResponse response) {
    var s = find(value);
    if (s != null) {
      String digest = hash(value.substring(value.indexOf('.') + 1));
      Number known =
          (Number)
              em.createNativeQuery(
                      "select count(*) from used_session_tokens where session_id=:s and"
                          + " token_hash=:h")
                  .setParameter("s", s.id)
                  .setParameter("h", digest)
                  .getSingleResult();
      if (digest.equals(s.tokenHash) || known.longValue() > 0) s.revoked = true;
    }
    cookie(response, "", Instant.EPOCH);
  }

  private Map<String, Object> accessToken(Account a, LoginSession s) {
    Instant now = Instant.now(), expiry = now.plusSeconds(600);
    var claims =
        JwtClaimsSet.builder()
            .issuer("my-society-api")
            .subject(a.id.toString())
            .claim("revision", a.tokenVersion)
            .claim("sid", s.id.toString())
            .issuedAt(now)
            .expiresAt(expiry)
            .id(UUID.randomUUID().toString())
            .build();
    return Map.of(
        "token",
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue(),
        "expiresAt",
        expiry,
        "sessionExpiresAt",
        s.expiresAt,
        "user",
        Views.of(a));
  }

  private String successor(String raw) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(key);
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              mac.doFinal(("session-rotation:" + raw).getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private ApiException invalid(HttpServletResponse response) {
    cookie(response, "", Instant.EPOCH);
    return new ApiException(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in.");
  }

  private void cookie(HttpServletResponse response, String value, Instant expires) {
    long age = Math.max(0, Duration.between(Instant.now(), expires).getSeconds());
    response.addHeader(
        "Set-Cookie",
        COOKIE
            + "="
            + value
            + "; Path=/; Max-Age="
            + age
            + "; Secure; HttpOnly; SameSite=None; Partitioned");
    response.setHeader("Cache-Control", "no-store");
  }
}
