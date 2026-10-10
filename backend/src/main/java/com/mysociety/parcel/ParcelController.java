package com.mysociety.parcel;

import com.mysociety.audit.*;
import com.mysociety.common.*;
import com.mysociety.flat.Flat;
import com.mysociety.notification.*;
import com.mysociety.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/parcels")
@Transactional
public class ParcelController {
  private final Store db;
  private final Access access;
  private final Visibility visibility;
  private final AuditService audit;
  private final NotificationService notifications;

  public ParcelController(
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
    access.require(s, "PARCEL_READ");
    return db.list(Parcel.class, s).stream().filter(visibility::parcel).map(Views::of).toList();
  }

  @PostMapping
  public Map<String, Object> create(
      @RequestHeader("X-Society-Id") UUID s, @RequestBody Map<String, Object> b) {
    access.require(s, "PARCEL_CREATE");
    Input i = new Input(b);
    Parcel p = new Parcel();
    p.societyId = s;
    p.flatId = i.requiredId("flatId");
    if (!db.tenant(Flat.class, s, p.flatId).active) throw ApiException.bad("Flat is inactive.");
    p.courier = i.required("courier", 100);
    p.description = i.required("description", 1000);
    p.status = "WAITING";
    p.createdBy = access.user().id;
    db.save(p);
    notifications.flat(s, p.flatId, "PARCEL_RECEIVED", "A parcel is waiting for collection", p);
    audit.log(s, "PARCEL_RECEIVED", p, null);
    return Views.of(p);
  }

  @PostMapping("/{id}/{action}")
  public Map<String, Object> action(
      @RequestHeader("X-Society-Id") UUID s, @PathVariable UUID id, @PathVariable String action) {
    access.require(s, "PARCEL_COLLECT");
    Parcel p = db.tenant(Parcel.class, s, id);
    visibility.require(visibility.parcel(p));
    String before = audit.snapshot(p);
    if (!"WAITING".equals(p.status))
      throw ApiException.conflict("This parcel is no longer waiting.");
    if ("collect".equals(action)) {
      p.status = "COLLECTED";
      p.collectedAt = Instant.now();
      p.collectedBy = access.user().id;
    } else if ("return".equals(action)) {
      access.require(s, "PARCEL_CREATE");
      p.status = "RETURNED";
    } else throw ApiException.missing();
    audit.log(s, "PARCEL_" + p.status, p, before);
    return Views.of(p);
  }
}
