package com.mysociety.notification;

import com.mysociety.common.*;
import com.mysociety.membership.Membership;
import com.mysociety.platform.RoleDefinition;
import com.mysociety.resident.*;
import com.mysociety.security.Permissions;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
  private final Store db;

  public NotificationService(Store db) {
    this.db = db;
  }

  public void user(
      UUID s, UUID user, String type, String title, String message, BaseEntity related) {
    if (user == null) return;
    Notification n = new Notification();
    n.societyId = s;
    n.userId = user;
    n.type = type;
    n.title = title;
    n.message = message;
    n.relatedEntityType = related.getClass().getSimpleName();
    n.relatedEntityId = related.id;
    db.save(n);
  }

  public void flat(UUID s, UUID flat, String type, String title, BaseEntity related) {
    LocalDate today =
        LocalDate.now(java.time.ZoneId.of(db.get(com.mysociety.society.Society.class, s).timezone));
    Set<UUID> ids = new HashSet<>();
    db.list(ResidentFlatMapping.class, s).stream()
        .filter(
            m ->
                m.flatId.equals(flat)
                    && (m.startDate == null || !m.startDate.isAfter(today))
                    && (m.endDate == null || m.endDate.isAfter(today)))
        .forEach(
            m -> {
              ResidentProfile r = db.tenant(ResidentProfile.class, s, m.residentId);
              if (r.active && r.userId != null) ids.add(r.userId);
            });
    db.list(Membership.class, s).stream()
        .filter(m -> ids.contains(m.userId) && "ACTIVE".equals(m.status))
        .forEach(
            m ->
                user(
                    s,
                    m.userId,
                    type,
                    title,
                    "Open the relevant section to view details.",
                    related));
  }

  public void permission(UUID s, String permission, String type, String title, BaseEntity related) {
    Set<String> roles = new HashSet<>();
    db.all(RoleDefinition.class).stream()
        .filter(r -> Permissions.split(r.permissions).contains(permission))
        .forEach(r -> roles.add(r.code));
    db.list(Membership.class, s).stream()
        .filter(
            m ->
                "ACTIVE".equals(m.status)
                    && !Collections.disjoint(roles, Permissions.split(m.roles)))
        .forEach(
            m ->
                user(
                    s,
                    m.userId,
                    type,
                    title,
                    "Open the relevant section to view details.",
                    related));
  }

  public void society(UUID s, String type, String title, BaseEntity related) {
    db.list(Membership.class, s).stream()
        .filter(m -> "ACTIVE".equals(m.status))
        .forEach(
            m ->
                user(
                    s,
                    m.userId,
                    type,
                    title,
                    "New information is available for your society.",
                    related));
  }
}
