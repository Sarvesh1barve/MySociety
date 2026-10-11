package com.mysociety.config;

import com.mysociety.common.*;
import com.mysociety.complaint.*;
import com.mysociety.emergency.*;
import com.mysociety.flat.*;
import com.mysociety.meeting.*;
import com.mysociety.membership.*;
import com.mysociety.notice.*;
import com.mysociety.parcel.*;
import com.mysociety.platform.*;
import com.mysociety.resident.*;
import com.mysociety.security.*;
import com.mysociety.society.*;
import com.mysociety.task.*;
import com.mysociety.user.*;
import com.mysociety.visitor.*;
import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements ApplicationRunner {
  private final Store db;
  private final Environment env;
  private final PasswordEncoder passwords;

  public Bootstrap(Store db, Environment env, PasswordEncoder passwords) {
    this.db = db;
    this.env = env;
    this.passwords = passwords;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (db.all(RoleDefinition.class).isEmpty())
      Permissions.defaults()
          .forEach(
              (code, perms) -> {
                RoleDefinition r = new RoleDefinition();
                r.code = code;
                r.permissions = String.join(",", new TreeSet<>(perms));
                db.save(r);
              });
    if (db.all(ApplicationSettings.class).isEmpty()) {
      ApplicationSettings a = new ApplicationSettings();
      a.applicationName = "My Society";
      a.shortName = "My Society";
      a.footerText = "A little closer. A better community.";
      a.updatedAt = Instant.now();
      db.save(a);
    }
    if (db.all(Account.class).stream().noneMatch(a -> a.superAdmin)) {
      String email = env.getProperty("INITIAL_SUPER_ADMIN_EMAIL"),
          mobile = env.getProperty("INITIAL_SUPER_ADMIN_MOBILE"),
          password = env.getProperty("INITIAL_SUPER_ADMIN_PASSWORD");
      if (password == null
          || password.length() < 12
          || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
          || (email == null || email.isBlank()) && (mobile == null || mobile.isBlank()))
        throw new IllegalStateException(
            "Set INITIAL_SUPER_ADMIN_EMAIL or MOBILE and a password of at least 12 characters.");
      Account owner =
          account(
              env.getProperty("INITIAL_SUPER_ADMIN_NAME", "Platform Owner"),
              email,
              mobile,
              password);
      owner.superAdmin = true;
    }
  }

  private Account account(String name, String email, String mobile, String password) {
    Account a = new Account();
    a.name = name;
    a.email = email == null || email.isBlank() ? null : email.toLowerCase(Locale.ROOT);
    a.mobile = mobile == null || mobile.isBlank() ? null : Input.normalizeMobile(mobile);
    a.passwordHash = passwords.encode(password);
    a.status = "ACTIVE";
    return db.save(a);
  }
}
