package com.mysociety.platform;

import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.membership.*;
import com.mysociety.security.*;
import com.mysociety.society.*;
import com.mysociety.user.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Transactional
public class PlatformController {
  private final Store db;
  private final Access access;
  private final AuditService audit;
  private final PasswordEncoder passwords;

  @Value("${server.port}")
  private int port;

  public PlatformController(
      Store db, Access access, AuditService audit, PasswordEncoder passwords) {
    this.db = db;
    this.access = access;
    this.audit = audit;
    this.passwords = passwords;
  }

  @GetMapping("/system/health")
  public Map<String, Object> health() {
    db.all(ApplicationSettings.class);
    return Map.of("status", "UP", "application", "My Society API", "version", "1.0.0");
  }

  @GetMapping("/application/config")
  public Map<String, Object> config() {
    var a = db.all(ApplicationSettings.class).getFirst();
    Map<String, Object> v = new LinkedHashMap<>(Views.of(a));
    v.remove("updatedBy");
    return v;
  }

  @PutMapping("/platform/branding")
  public Map<String, Object> branding(@RequestBody Map<String, Object> body) {
    access.platform();
    Input i = new Input(body);
    var a = db.all(ApplicationSettings.class).getFirst();
    String before = audit.snapshot(a);
    a.applicationName = i.required("applicationName", 80);
    a.shortName = i.required("shortName", 30);
    a.logoUrl = url(i.text("logoUrl", 1000));
    a.faviconUrl = url(i.text("faviconUrl", 1000));
    a.footerText = i.text("footerText", 200);
    a.supportEmail = i.email("supportEmail");
    a.supportPhone = i.mobile("supportPhone", false);
    a.updatedAt = Instant.now();
    a.updatedBy = access.user().id;
    audit.log(null, "APPLICATION_BRANDING_CHANGED", a, before);
    return Views.of(a);
  }

  private String url(String s) {
    if (s != null && !s.startsWith("https://"))
      throw ApiException.bad("Image URLs must use HTTPS.");
    return s;
  }

  @GetMapping("/platform/societies")
  public List<Map<String, Object>> societies() {
    access.platform();
    return db.all(Society.class).stream().map(Views::of).toList();
  }

  @PostMapping("/platform/societies")
  public Map<String, Object> createSociety(@RequestBody Map<String, Object> b) {
    access.platform();
    Society s = new Society();
    applySociety(s, new Input(b));
    db.save(s);
    audit.log(null, "SOCIETY_CREATED", s, null);
    return Views.of(s);
  }

  @PutMapping("/platform/societies/{id}")
  public Map<String, Object> editSociety(
      @PathVariable UUID id, @RequestBody Map<String, Object> b) {
    access.platform();
    Society s = db.get(Society.class, id);
    String before = audit.snapshot(s);
    applySociety(s, new Input(b));
    audit.log(null, "SOCIETY_UPDATED", s, before);
    return Views.of(s);
  }

  public static void applySociety(Society s, Input i) {
    s.name = i.required("name", 160);
    s.code = i.required("code", 30).toUpperCase(Locale.ROOT);
    if (!s.code.matches("[A-Z0-9_-]+"))
      throw ApiException.bad("Society code must contain letters, numbers, - or _.");
    s.registrationNumber = i.text("registrationNumber", 100);
    s.address = i.text("address", 500);
    s.city = i.text("city", 100);
    s.state = i.text("state", 100);
    s.pinCode = i.text("pinCode", 12);
    s.timezone = i.value("timezone", "Asia/Kolkata");
    try {
      ZoneId.of(s.timezone);
    } catch (Exception e) {
      throw ApiException.bad("Invalid timezone.");
    }
    s.email = i.email("email");
    s.phone = i.mobile("phone", false);
    s.status = i.choice("status", "ACTIVE", "ACTIVE", "INACTIVE", "SUSPENDED", "TRIAL");
    s.logoUrl = i.text("logoUrl", 1000);
    if (s.logoUrl != null && !s.logoUrl.startsWith("https://"))
      throw ApiException.bad("Logo URL must use HTTPS.");
  }

  @GetMapping("/platform/users")
  public List<Map<String, Object>> users() {
    access.platform();
    return db.all(Account.class).stream().map(Views::of).toList();
  }

  @PostMapping("/platform/users")
  public Map<String, Object> createUser(@RequestBody Map<String, Object> b) {
    access.platform();
    Account u = newUser(new Input(b));
    audit.log(null, "USER_CREATED", u, null);
    return Views.of(u);
  }

  public Account newUser(Input i) {
    Account u = new Account();
    u.name = i.required("name", 120);
    u.email = i.email("email");
    u.mobile = i.mobile("mobile", false);
    if (u.email == null && u.mobile == null) throw ApiException.bad("Email or mobile is required.");
    String p = i.required("password", 72);
    if (p.length() < 12 || p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw ApiException.bad(
          "Use at least 12 characters and at most 72 UTF-8 bytes for the password.");
    u.passwordHash = passwords.encode(p);
    u.status = "ACTIVE";
    u.superAdmin = false;
    return db.save(u);
  }

  @PatchMapping("/platform/users/{id}")
  public Map<String, Object> userStatus(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    access.platform();
    Account a = db.get(Account.class, id);
    if (a.superAdmin) throw ApiException.bad("Platform owner status cannot be changed here.");
    String before = audit.snapshot(a);
    a.status = new Input(b).choice("status", a.status, "ACTIVE", "SUSPENDED", "INACTIVE");
    audit.log(null, "USER_STATUS_CHANGED", a, before);
    return Views.of(a);
  }

  @GetMapping("/platform/roles")
  public Map<String, Object> roles() {
    access.platform();
    return Map.of(
        "roles",
        db.all(RoleDefinition.class).stream().map(Views::of).toList(),
        "permissions",
        Permissions.ALL.stream().sorted().toList());
  }

  @PutMapping("/platform/roles/{id}")
  public Map<String, Object> updateRole(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    access.platform();
    RoleDefinition r = db.get(RoleDefinition.class, id);
    String before = audit.snapshot(r);
    String p = new Input(b).required("permissions", 5000);
    Set<String> chosen = Permissions.split(p);
    if (!Permissions.ALL.containsAll(chosen) || !Collections.disjoint(chosen, Permissions.PLATFORM))
      throw ApiException.bad("Invalid society permissions.");
    r.permissions = String.join(",", new TreeSet<>(chosen));
    audit.log(null, "ROLE_PERMISSION_CHANGED", r, before);
    return Views.of(r);
  }

  @GetMapping("/platform/audit")
  public List<Map<String, Object>> audit() {
    access.platform();
    return db.all(AuditLog.class).stream().limit(500).map(Views::of).toList();
  }

  @GetMapping("/system/diagnostics")
  public Map<String, Object> diagnostics() {
    access.platform();
    return Map.of(
        "version",
        "1.0.0",
        "port",
        port,
        "database",
        "UP",
        "totalSocieties",
        db.all(Society.class).size(),
        "activeSocieties",
        db.all(Society.class).stream().filter(s -> "ACTIVE".equals(s.status)).count(),
        "totalUsers",
        db.all(Account.class).size());
  }
}
