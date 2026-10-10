package com.mysociety.common;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public class Input {
  private final Map<String, Object> data;

  public Input(Map<String, Object> data) {
    this.data = data;
  }

  public boolean has(String key) {
    return data.containsKey(key);
  }

  public String text(String key, int max) {
    Object v = data.get(key);
    if (v == null) return null;
    if (!(v instanceof String s)) throw ApiException.bad(key + " must be text.");
    s = s.trim();
    if (s.length() > max) throw ApiException.bad(key + " is too long.");
    return s.isEmpty() ? null : s;
  }

  public String required(String key, int max) {
    String s = text(key, max);
    if (s == null) throw ApiException.bad(key + " is required.");
    return s;
  }

  public String value(String key, String fallback) {
    String s = text(key, 120);
    return s == null ? fallback : s;
  }

  public String choice(String key, String fallback, String... options) {
    String v = value(key, fallback);
    if (v == null || !Set.of(options).contains(v)) throw ApiException.bad("Invalid " + key + ".");
    return v;
  }

  public UUID id(String key) {
    String s = text(key, 36);
    try {
      return s == null ? null : UUID.fromString(s);
    } catch (Exception e) {
      throw ApiException.bad("Invalid " + key + ".");
    }
  }

  public UUID requiredId(String key) {
    UUID id = id(key);
    if (id == null) throw ApiException.bad(key + " is required.");
    return id;
  }

  public Instant instant(String key) {
    String s = text(key, 40);
    try {
      return s == null ? null : Instant.parse(s);
    } catch (Exception e) {
      throw ApiException.bad(key + " must be an ISO timestamp with timezone.");
    }
  }

  public LocalDate date(String key) {
    String s = text(key, 10);
    try {
      return s == null ? null : LocalDate.parse(s);
    } catch (Exception e) {
      throw ApiException.bad(key + " must be YYYY-MM-DD.");
    }
  }

  public boolean bool(String key, boolean fallback) {
    Object v = data.get(key);
    if (v == null) return fallback;
    if (!(v instanceof Boolean)) throw ApiException.bad(key + " must be true or false.");
    return (boolean) v;
  }

  public BigDecimal decimal(String key) {
    try {
      Object x = data.get(key);
      BigDecimal n = x == null ? null : new BigDecimal(x.toString());
      if (n != null && (n.signum() < 0 || n.compareTo(new BigDecimal("10000000")) > 0))
        throw new IllegalArgumentException();
      return n;
    } catch (Exception e) {
      throw ApiException.bad("Invalid " + key + ".");
    }
  }

  public String mobile(String key, boolean required) {
    String v = required ? required(key, 20) : text(key, 20);
    if (v != null && !v.matches("\\+?[0-9][0-9 -]{6,18}"))
      throw ApiException.bad("Enter a valid phone number.");
    return v;
  }

  public String email(String key) {
    String v = text(key, 254);
    if (v != null && !v.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
      throw ApiException.bad("Enter a valid email address.");
    return v == null ? null : v.toLowerCase(Locale.ROOT);
  }

  public String ids(String key) {
    String s = text(key, 8000);
    if (s == null) return "";
    for (String id : s.split(",")) {
      try {
        UUID.fromString(id.trim());
      } catch (Exception e) {
        throw ApiException.bad(key + " must contain comma-separated IDs.");
      }
    }
    return s.replace(" ", "");
  }

  public static List<UUID> parseIds(String s) {
    return s == null || s.isBlank()
        ? List.of()
        : Arrays.stream(s.split(",")).map(UUID::fromString).toList();
  }

  public Object raw(String key) {
    return data.get(key);
  }
}
