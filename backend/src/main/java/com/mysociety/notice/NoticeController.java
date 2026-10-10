package com.mysociety.notice;

import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.flat.*;
import com.mysociety.notification.*;
import com.mysociety.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notices")
@Transactional
public class NoticeController {
  private final Store db;
  private final Access access;
  private final Visibility visibility;
  private final AuditService audit;
  private final NotificationService notifications;

  public NoticeController(
      Store db,
      Access access,
      Visibility visibility,
      AuditService audit,
      NotificationService notifications) {
    this.db = db;
    this.access = access;
    this.visibility = visibility;
    this.audit = audit;
    this.notifications = notifications;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestHeader("X-Society-Id") UUID s) {
    access.require(s, "NOTICE_READ");
    UUID uid = access.user().id;
    Set<UUID> ack = new HashSet<>();
    db.list(Acknowledgement.class, s).stream()
        .filter(a -> a.userId.equals(uid))
        .forEach(a -> ack.add(a.noticeId));
    return db.list(Notice.class, s).stream()
        .filter(visibility::notice)
        .map(
            n -> {
              var v = Views.of(n);
              v.put("acknowledged", ack.contains(n.id));
              v.put(
                  "acknowledgementCount",
                  access.can(s, "NOTICE_CREATE")
                      ? db.list(Acknowledgement.class, s).stream()
                          .filter(a -> a.noticeId.equals(n.id))
                          .count()
                      : null);
              return v;
            })
        .toList();
  }

  @PostMapping
  public Map<String, Object> create(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "NOTICE_CREATE");
    Notice n = new Notice();
    n.societyId = s;
    apply(n, new Input(b));
    n.createdBy = access.user().id;
    db.save(n);
    // Generic notification text avoids revealing targeted notice contents to other audiences.
    notifications.society(s, "NOTICE_CREATED", "Check the notice board for updates", n);
    audit.log(s, "NOTICE_CREATED", n, null);
    return Views.of(n);
  }

  @PutMapping("/{id}")
  public Map<String, Object> edit(
      @RequestHeader("X-Society-Id") UUID s,
      @PathVariable UUID id,
      @RequestBody Map<String, Object> b) {
    access.require(s, "NOTICE_CREATE");
    Notice n = db.tenant(Notice.class, s, id);
    if (n.publishDate != null && !n.publishDate.isAfter(Instant.now()))
      throw ApiException.conflict(
          "Published notices are immutable. Publish a correction as a new notice.");
    String before = audit.snapshot(n);
    apply(n, new Input(b));
    audit.log(s, "NOTICE_UPDATED", n, before);
    return Views.of(n);
  }

  private void apply(Notice n, Input i) {
    n.title = i.required("title", 180);
    n.content = i.required("content", 12000);
    n.noticeType =
        i.choice(
            "noticeType",
            "GENERAL",
            "GENERAL",
            "IMPORTANT",
            "EMERGENCY",
            "MAINTENANCE",
            "WATER_SHUTDOWN",
            "POWER_SHUTDOWN",
            "MEETING",
            "EVENT",
            "GOVERNMENT_LEGAL",
            "REPAIR_WORK");
    n.audience =
        i.choice(
            "audience",
            "ENTIRE_SOCIETY",
            "ENTIRE_SOCIETY",
            "SPECIFIC_WING",
            "SPECIFIC_FLOOR",
            "SPECIFIC_FLATS",
            "OWNERS_ONLY",
            "TENANTS_ONLY");
    n.targetIds = i.ids("targetIds");
    List<UUID> ids = Input.parseIds(n.targetIds);
    if (n.audience.startsWith("SPECIFIC") && ids.isEmpty())
      throw ApiException.bad("Select at least one audience target.");
    for (UUID id : ids)
      switch (n.audience) {
        case "SPECIFIC_WING" -> db.tenant(Wing.class, n.societyId, id);
        case "SPECIFIC_FLOOR" -> db.tenant(Floor.class, n.societyId, id);
        case "SPECIFIC_FLATS" -> db.tenant(Flat.class, n.societyId, id);
        default -> throw ApiException.bad("Targets are only used for specific audiences.");
      }
    n.publishDate = Optional.ofNullable(i.instant("publishDate")).orElse(Instant.now());
    n.expiryDate = i.instant("expiryDate");
    if (n.expiryDate != null && !n.expiryDate.isAfter(n.publishDate))
      throw ApiException.bad("Expiry must follow publication.");
    n.attachmentId = i.id("attachmentId");
    if (n.attachmentId != null) visibility.attachments(n.societyId, n.attachmentId.toString());
    n.acknowledgementRequired = i.bool("acknowledgementRequired", false);
  }

  @PostMapping("/{id}/acknowledge")
  public Map<String, Object> acknowledge(
      @RequestHeader("X-Society-Id") UUID s, @PathVariable UUID id) {
    access.require(s, "NOTICE_ACKNOWLEDGE");
    Notice n = db.tenant(Notice.class, s, id);
    visibility.require(visibility.notice(n));
    if (!n.acknowledgementRequired)
      throw ApiException.bad("This notice does not require acknowledgement.");
    var existing =
        db.query(
            Acknowledgement.class,
            "societyId=:s and noticeId=:n and userId=:u",
            Map.of("s", s, "n", id, "u", access.user().id));
    if (!existing.isEmpty()) return Views.of(existing.getFirst());
    Acknowledgement a = new Acknowledgement();
    a.societyId = s;
    a.noticeId = id;
    a.userId = access.user().id;
    db.save(a);
    audit.log(s, "NOTICE_ACKNOWLEDGED", a, null);
    return Views.of(a);
  }
}
